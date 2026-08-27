package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.pay.PaymentChannel;
import com.dsh.platform.dto.BiddingDtos.*;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BiddingService {

    private final QuotationMapper quotationMapper;
    private final DemandMapper demandMapper;
    private final ProcessMapper processMapper;
    private final CapabilityService capabilityService;
    private final ObjectMapper objectMapper;
    private final FundLedger fundLedger;
    private final ProcessCoverageService coverageService;
    private final SiteNotify siteNotify;
    private final PaymentChannel paymentChannel;
    private final DeviceService deviceService;

    @Value("${dsh.fee.deposit-rate}")
    private BigDecimal depositRate;

    @Transactional
    public IntentionStart intention(IntentionRequest req) {
        Demand d = demandMapper.selectById(req.demandId());
        if (d == null || !"PUBLISHED".equals(d.getStatus())) {
            throw new BizException("需求不在意向期，无法报名");
        }
        if (req.processNo() == null) {
            throw new BizException("请选择工序");
        }
        if (req.minQty() == null || req.maxQty() == null || req.minQty() <= 0 || req.maxQty() <= 0) {
            throw new BizException("请填写有效的承接量区间");
        }
        if (req.minQty() > req.maxQty()) {
            throw new BizException("最小承接量不能大于最大承接量");
        }
        Process proc = processMapper.selectOne(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, d.getId())
                .eq(Process::getProcessNo, req.processNo())
                .last("limit 1"));
        int cap = proc != null && proc.getQuantity() != null ? proc.getQuantity()
                : (d.getQuantity() == null ? Integer.MAX_VALUE : d.getQuantity());
        if (req.maxQty() > cap) {
            throw new BizException("最大承接量不能超过该工序需求数量");
        }

        JsonNode capJson = capabilityService.requireComplete(UserContext.tenantId());
        deviceService.assertSelectable(UserContext.tenantId(), req.deviceIds());
        Quotation existing = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getProcessNo, req.processNo())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .in(Quotation::getStatus, "INTENTION", "LOCKED")
                .last("limit 1"));
        if (existing != null) {
            if ("LOCKED".equals(existing.getStatus()) || "FROZEN".equals(existing.getIntentionStatus())) {
                throw new BizException("该工序已报名，请勿重复提交");
            }
            existing.setDeviceIdsJson(deviceService.writeIds(req.deviceIds()));
            quotationMapper.updateById(existing);
            return payIntention(existing);
        }

        Quotation q = new Quotation();
        q.setTenantId(UserContext.tenantId());
        q.setDemandId(req.demandId());
        q.setProcessNo(req.processNo());
        q.setIntentionPrice(null);
        q.setMinQty(req.minQty());
        q.setMaxQty(req.maxQty());
        q.setValidDays(null);
        q.setExtraJson(snapshot(capJson, req.processNo()));
        q.setDeviceIdsJson(deviceService.writeIds(req.deviceIds()));
        q.setIntentionStatus("PENDING_PAY");
        q.setDepositStatus("NONE");
        q.setStatus("INTENTION");
        q.setVersion(1);
        quotationMapper.insert(q);
        return payIntention(q);
    }

    @Transactional
    public IntentionStart continueIntentionPay(Long quotationId) {
        Quotation q = quotationMapper.selectById(quotationId);
        if (q == null || !UserContext.tenantId().equals(q.getTenantId())) {
            throw new BizException("报名不存在或无权操作");
        }
        if (!"INTENTION".equals(q.getStatus()) || !"PENDING_PAY".equals(q.getIntentionStatus())) {
            throw new BizException("当前报名无需继续支付");
        }
        return payIntention(q);
    }

    private IntentionStart payIntention(Quotation q) {
        var started = paymentChannel.startIntention(q.getId(), fundLedger.intentionAmount());
        if (started.held()) {
            fundLedger.freezeIntention(q);
            notifyCoverage(q.getDemandId());
            return new IntentionStart(q.getId(), true, null);
        }
        q.setIntentionStatus("PENDING_PAY");
        quotationMapper.updateById(q);
        return new IntentionStart(q.getId(), false, started.payUrl());
    }

    @Transactional
    public void confirmIntentionPaid(Long quotationId, BigDecimal paidAmount) {
        Quotation q = quotationMapper.selectById(quotationId);
        if (q == null) {
            throw new BizException("报名不存在");
        }
        if ("FROZEN".equals(q.getIntentionStatus())) {
            return;
        }
        BigDecimal expect = fundLedger.intentionAmount();
        if (paidAmount != null && paidAmount.subtract(expect).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("回调金额与意向金不一致");
        }
        fundLedger.freezeIntention(q);
        notifyCoverage(q.getDemandId());
    }

    private void notifyCoverage(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            return;
        }
        CoverageView view = coverageService.of(demandId);
        siteNotify.send(d.getTenantId(), "覆盖度更新#" + demandId,
                "需求「" + d.getTitle() + "」有新的工厂报名。" + coverageService.unsatisfiedText(view)
                        + (view.allSatisfied() ? "各工序产能已满足。" : ""));
    }

    @Transactional
    public void cancelIntention(Long quotationId) {
        Quotation q = quotationMapper.selectById(quotationId);
        if (q == null || !UserContext.tenantId().equals(q.getTenantId())) {
            throw new BizException("报名不存在或无权操作");
        }
        if (!"INTENTION".equals(q.getStatus())) {
            throw new BizException("当前阶段不可取消");
        }
        fundLedger.unfreezeIntention(q);
    }

    @Transactional
    public void cancelLock(Long quotationId) {
        Quotation q = quotationMapper.selectById(quotationId);
        if (q == null || !UserContext.tenantId().equals(q.getTenantId())) {
            throw new BizException("报名不存在或无权操作");
        }
        Demand d = demandMapper.selectById(q.getDemandId());
        if (d == null || !"LOCKING".equals(d.getStatus()) || !"LOCKED".equals(q.getStatus())) {
            throw new BizException("仅保证金期可取消锁定报价，将扣除保证金");
        }
        fundLedger.forfeitDeposit(q);
    }

    @Transactional
    public Long lock(LockRequest req) {
        Demand d = demandMapper.selectById(req.demandId());
        if (d == null || !"LOCKING".equals(d.getStatus())) {
            throw new BizException("需求不在保证金期，无法锁定报价");
        }
        if (req.price() == null || req.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("请填写锁定报价");
        }
        if (req.promisedDays() != null && d.getDeadlineHard() != null) {
            long maxDays = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), d.getDeadlineHard());
            if (req.promisedDays() > maxDays) {
                throw new BizException("承诺工期不能超过硬交期");
            }
        }
        Quotation q = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getProcessNo, req.processNo())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION")
                .eq(Quotation::getIntentionStatus, "FROZEN")
                .last("limit 1"));
        if (q == null) {
            throw new BizException("请先完成意向报名并支付意向金");
        }
        q.setPrice(req.price());
        q.setYieldRate(req.yieldRate());
        q.setPromisedDays(req.promisedDays());
        q.setMinQty(req.minQty());
        q.setMaxQty(req.maxQty());
        q.setStageCurveJson(req.stageCurveJson());
        q.setStatus("LOCKED");
        quotationMapper.updateById(q);
        fundLedger.freezeDeposit(q, req.price().multiply(depositRate));
        return q.getId();
    }

    public List<Quotation> listMine() {
        List<Quotation> list = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .orderByDesc(Quotation::getId));
        for (Quotation q : list) {
            Demand d = demandMapper.selectById(q.getDemandId());
            q.setDemandTitle(d == null ? "" : d.getTitle());
        }
        return list;
    }

    public List<Quotation> listByDemand(Long demandId) {
        return quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .orderByAsc(Quotation::getProcessNo));
    }

    private String snapshot(JsonNode cap, Integer processNo) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("processNo", processNo);
            snap.put("devices", cap.get("devices"));
            snap.put("certs", cap.get("certs"));
            snap.put("yieldRate", cap.get("yieldRate"));
            JsonNode caps = cap.get("capacityByProcess");
            int daily = 0;
            if (caps != null && caps.isArray() && !caps.isEmpty()) {
                daily = caps.get(0).path("dailyCapacity").asInt(0);
                for (JsonNode c : caps) {
                    if (c.path("processName").asText("").contains(String.valueOf(processNo))) {
                        daily = c.path("dailyCapacity").asInt(daily);
                    }
                }
            }
            snap.put("dailyCapacity", daily);
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return "{}";
        }
    }
}
