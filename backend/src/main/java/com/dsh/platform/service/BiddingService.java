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
        List<IntentionItem> items = normalizeItems(req);
        JsonNode capJson = capabilityService.requireComplete(UserContext.tenantId());

        Quotation first = null;
        for (IntentionItem item : items) {
            Process proc = processMapper.selectOne(new LambdaQueryWrapper<Process>()
                    .eq(Process::getDemandId, d.getId())
                    .eq(Process::getProcessNo, item.processNo())
                    .last("limit 1"));
            int cap = proc != null && proc.getQuantity() != null ? proc.getQuantity()
                    : (d.getQuantity() == null ? Integer.MAX_VALUE : d.getQuantity());
            if (item.maxQty() > cap) {
                throw new BizException("工序 " + item.processNo() + " 最大承接量不能超过需求数量 " + cap);
            }
            Quotation existing = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                    .eq(Quotation::getDemandId, req.demandId())
                    .eq(Quotation::getProcessNo, item.processNo())
                    .eq(Quotation::getTenantId, UserContext.tenantId())
                    .in(Quotation::getStatus, "INTENTION", "LOCKED")
                    .last("limit 1"));
            if (existing != null) {
                if ("LOCKED".equals(existing.getStatus()) || "FROZEN".equals(existing.getIntentionStatus())
                        || "COVERED".equals(existing.getIntentionStatus())) {
                    throw new BizException("工序 " + item.processNo() + " 已报名，请勿重复提交");
                }
                existing.setMinQty(item.minQty());
                existing.setMaxQty(item.maxQty());
                existing.setDeviceIdsJson(deviceService.writeIds(item.deviceIds() == null ? List.of() : item.deviceIds()));
                quotationMapper.updateById(existing);
                if (first == null) {
                    first = existing;
                } else {
                    markCovered(existing);
                }
                continue;
            }
            Quotation q = new Quotation();
            q.setTenantId(UserContext.tenantId());
            q.setDemandId(req.demandId());
            q.setProcessNo(item.processNo());
            q.setIntentionPrice(null);
            q.setMinQty(item.minQty());
            q.setMaxQty(item.maxQty());
            q.setValidDays(null);
            q.setExtraJson(snapshot(capJson, item.processNo()));
            q.setDeviceIdsJson(deviceService.writeIds(item.deviceIds() == null ? List.of() : item.deviceIds()));
            q.setIntentionStatus(first == null ? "PENDING_PAY" : "COVERED");
            q.setDepositStatus("NONE");
            q.setStatus("INTENTION");
            q.setVersion(1);
            quotationMapper.insert(q);
            if (first == null) {
                first = q;
            }
        }
        // 已有同单其他工序在缴费/已缴：本次全部并入，不再重复收意向金
        Quotation paid = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getIntentionStatus, "FROZEN")
                .ne(Quotation::getId, first.getId())
                .last("limit 1"));
        if (paid != null) {
            markCovered(first);
            return new IntentionStart(first.getId(), true, null);
        }
        return payIntention(first);
    }

    private void markCovered(Quotation q) {
        q.setIntentionStatus("COVERED");
        quotationMapper.updateById(q);
    }

    private List<IntentionItem> normalizeItems(IntentionRequest req) {
        List<IntentionItem> items = req.items() != null && !req.items().isEmpty()
                ? req.items()
                : (req.processNo() == null ? List.of()
                : List.of(new IntentionItem(req.processNo(), req.minQty(), req.maxQty(), req.deviceIds())));
        if (items.isEmpty()) {
            throw new BizException("请选择工序");
        }
        for (IntentionItem item : items) {
            if (item.processNo() == null) {
                throw new BizException("请选择工序");
            }
            if (item.minQty() == null || item.maxQty() == null || item.minQty() <= 0 || item.maxQty() <= 0) {
                throw new BizException("工序 " + item.processNo() + " 请填写有效的承接量区间");
            }
            if (item.minQty() > item.maxQty()) {
                throw new BizException("工序 " + item.processNo() + " 最小承接量不能大于最大承接量");
            }
        }
        return items;
    }

    /** 工厂思考期填报：实施方案+单价+分期交付，冻结总报价 5% 保证金。 */
    @Transactional
    public void commit(CommitRequest req) {
        Demand d = demandMapper.selectById(req.demandId());
        if (d == null || !"FACTORY_THINKING".equals(d.getStatus())) {
            throw new BizException("需求不在工厂思考期，无法填报");
        }
        if (req.planText() == null || req.planText().isBlank()) {
            throw new BizException("请填写实施方案");
        }
        int periods = d.getDeliveryTimes() == null || d.getDeliveryTimes() <= 0 ? 1 : d.getDeliveryTimes();
        if (req.deliveryPlan() == null || req.deliveryPlan().size() != periods
                || req.deliveryPlan().stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new BizException("请按买家要求填写全部 " + periods + " 期交付内容");
        }
        List<Quotation> mine = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION")
                .in(Quotation::getIntentionStatus, "FROZEN", "COVERED"));
        if (mine.isEmpty()) {
            throw new BizException("你没有该需求的有效报名");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException("请填写各工序单价");
        }
        String deliveryJson = writeJson(req.deliveryPlan());
        for (Quotation q : mine) {
            CommitItem item = req.items().stream()
                    .filter(it -> it.processNo() != null && it.processNo().equals(q.getProcessNo()))
                    .findFirst()
                    .orElseThrow(() -> new BizException("工序 " + q.getProcessNo() + " 缺少单价，请全部填报"));
            if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("工序 " + q.getProcessNo() + " 单价必须大于 0");
            }
            if (item.promisedDays() != null && d.getDeadlineHard() != null) {
                long maxDays = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), d.getDeadlineHard());
                if (item.promisedDays() > maxDays) {
                    throw new BizException("工序 " + q.getProcessNo() + " 承诺工期不能超过硬交期");
                }
            }
            int qty = q.getMaxQty() == null ? 0 : q.getMaxQty();
            BigDecimal total = item.unitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            q.setUnitPrice(item.unitPrice());
            q.setPrice(total);
            q.setYieldRate(item.yieldRate());
            q.setPromisedDays(item.promisedDays());
            q.setPlanText(req.planText().trim());
            q.setDeliveryPlanJson(deliveryJson);
            q.setStatus("LOCKED");
            quotationMapper.updateById(q);
            fundLedger.freezeDeposit(q, total.multiply(depositRate).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        siteNotify.send(d.getTenantId(), "工厂已填报#" + d.getId(),
                "需求「" + d.getTitle() + "」有工厂完成思考期填报（详情在方案核定期可见）。");
    }

    /** 工厂思考期退出：不参加，退回意向金。 */
    @Transactional
    public void exitDemand(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null || !"FACTORY_THINKING".equals(d.getStatus())) {
            throw new BizException("需求不在工厂思考期，无法退出");
        }
        List<Quotation> mine = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION"));
        if (mine.isEmpty()) {
            throw new BizException("你没有可退出的报名");
        }
        for (Quotation q : mine) {
            fundLedger.unfreezeIntention(q);
        }
        deviceService.releaseByQuotations(mine);
        siteNotify.send(d.getTenantId(), "有工厂退出#" + demandId,
                "需求「" + d.getTitle() + "」有报名工厂在思考期退出，意向金已退回该厂。");
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

    /** 取消报名：意向金按单收，一并取消该需求下本厂全部工序报名。 */
    @Transactional
    public void cancelIntention(Long quotationId) {
        Quotation q = quotationMapper.selectById(quotationId);
        if (q == null || !UserContext.tenantId().equals(q.getTenantId())) {
            throw new BizException("报名不存在或无权操作");
        }
        if (!"INTENTION".equals(q.getStatus())) {
            throw new BizException("当前阶段不可取消");
        }
        List<Quotation> all = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, q.getDemandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION"));
        for (Quotation each : all) {
            fundLedger.unfreezeIntention(each);
        }
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
        List<Quotation> list = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .orderByAsc(Quotation::getProcessNo));
        // 买家在方案下发前看不到各厂报价与方案细节（只看报名/填报状态）
        String role = UserContext.role();
        if ("BUYER".equals(role)) {
            Demand d = demandMapper.selectById(demandId);
            String st = d == null ? "" : d.getStatus();
            boolean beforeSolution = "PUBLISHED".equals(st) || "FACTORY_THINKING".equals(st)
                    || "BUYER_THINKING".equals(st);
            if (beforeSolution) {
                for (Quotation q : list) {
                    q.setUnitPrice(null);
                    q.setPrice(null);
                    q.setPlanText(null);
                    q.setDeliveryPlanJson(null);
                    q.setIntentionPrice(null);
                }
            }
        }
        return list;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
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
