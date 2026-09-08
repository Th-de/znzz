package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.pay.PaymentChannel;
import com.dsh.platform.dto.BiddingDtos.*;
import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.mapper.AttachmentMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BiddingService {

    private final QuotationMapper quotationMapper;
    private final DemandMapper demandMapper;
    private final CapabilityService capabilityService;
    private final ObjectMapper objectMapper;
    private final FundLedger fundLedger;
    private final ProcessCoverageService coverageService;
    private final SiteNotify siteNotify;
    private final PaymentChannel paymentChannel;
    private final DeviceService deviceService;
    private final AuditService auditService;
    private final OrderMapper orderMapper;
    private final ContractMapper contractMapper;
    private final AttachmentMapper attachmentMapper;
    private final CreditScoring creditScoring;
    private final EnterpriseMapper enterpriseMapper;

    @Value("${dsh.fee.deposit-rate}")
    private BigDecimal depositRate;
    @Value("${dsh.time.factory-thinking-hours:24}")
    private int factoryThinkingHours;
    @Value("${dsh.time.buyer-thinking-hours:24}")
    private int buyerThinkingHours;
    @Value("${dsh.time.contract-sign-hours:24}")
    private int contractSignHours;

    @Transactional
    public IntentionStart intention(IntentionRequest req) {
        Demand d = demandMapper.selectById(req.demandId());
        if (d == null || !"PUBLISHED".equals(d.getStatus())) {
            throw new BizException("需求不在意向期，无法报名");
        }
        Enterprise self = enterpriseMapper.selectById(UserContext.tenantId());
        int credit = self == null || self.getCreditScore() == null ? 0 : self.getCreditScore();
        int needCredit = d.getMinCreditScore() == null ? 0 : d.getMinCreditScore();
        if (credit < needCredit) {
            throw new BizException("信用分 " + credit + " 未达到该需求最低要求 " + needCredit + "，无法报名");
        }
        IntentionItem item = normalizeItem(req);
        JsonNode capJson = capabilityService.requireComplete(UserContext.tenantId());
        int cap = d.getQuantity() == null ? Integer.MAX_VALUE : d.getQuantity();
        if (item.maxQty() > cap) {
            throw new BizException("最大承接量不能超过需求零件数 " + cap);
        }
        Quotation existing = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .in(Quotation::getStatus, "INTENTION", "LOCKED")
                .last("limit 1"));
        if (existing != null) {
            throw new BizException("该需求已报名，如需改承接量请先取消报名再重新填报");
        }
        Quotation first = new Quotation();
        first.setTenantId(UserContext.tenantId());
        first.setDemandId(req.demandId());
        first.setProcessNo(1);
        first.setIntentionPrice(null);
        first.setMinQty(item.minQty());
        first.setMaxQty(item.maxQty());
        first.setValidDays(null);
        first.setExtraJson(snapshot(capJson, 1));
        first.setDeviceIdsJson(deviceService.writeIds(item.deviceIds() == null ? List.of() : item.deviceIds()));
        first.setIntentionStatus("PENDING_PAY");
        first.setDepositStatus("NONE");
        first.setStatus("INTENTION");
        first.setVersion(1);
        quotationMapper.insert(first);
        // 已有同单其他工序在缴费/已缴：本次全部并入，不再重复收意向金
        Quotation paid = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION")
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

    /** 工期、良率以买家发布需求为准，工厂报价不再填报。 */
    private static Integer promisedDaysOf(Demand d) {
        if (d == null || d.getDeadlineHard() == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), d.getDeadlineHard());
        return (int) Math.max(days, 1);
    }

    private IntentionItem normalizeItem(IntentionRequest req) {
        IntentionItem item = req.items() != null && !req.items().isEmpty()
                ? req.items().get(0)
                : new IntentionItem(1, req.minQty(), req.maxQty(), req.deviceIds());
        if (item.minQty() == null || item.maxQty() == null || item.minQty() <= 0 || item.maxQty() <= 0) {
            throw new BizException("请填写有效的承接零件数区间");
        }
        if (item.minQty() > item.maxQty()) {
            throw new BizException("最小承接量不能大于最大承接量");
        }
        return item;
    }

    /** 工厂思考期填报：实施方案+单价，按意向承接区间最高值×单价冻结 5% 保证金。意向期只能报名，不能报价。 */
    @Transactional
    public void commit(CommitRequest req) {
        Demand d = demandMapper.selectById(req.demandId());
        if (d == null || !"FACTORY_THINKING".equals(d.getStatus())) {
            throw new BizException("意向期结束后进入工厂思考期才可填报报价");
        }
        if (req.planText() == null || req.planText().isBlank()) {
            throw new BizException("请填写实施方案");
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
            throw new BizException("请填写该品单价");
        }
        String deliveryJson = req.deliveryPlan() == null ? "[]" : writeJson(req.deliveryPlan());
        CommitItem priced = req.items().stream()
                .filter(it -> it.unitPrice() != null && it.unitPrice().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseThrow(() -> new BizException("请填写该品单价"));
        boolean depositFrozen = false;
        Integer promisedDays = promisedDaysOf(d);
        BigDecimal yieldRate = d.getMinYield();
        for (Quotation q : mine) {
            int min = q.getMinQty() == null ? 0 : q.getMinQty();
            int max = q.getMaxQty() == null ? 0 : q.getMaxQty();
            int qty = Math.max(min, max); // 保证金按意向承接区间最高值
            BigDecimal total = priced.unitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            q.setUnitPrice(priced.unitPrice());
            q.setPrice(total);
            q.setYieldRate(yieldRate);
            q.setPromisedDays(promisedDays);
            q.setPlanText(req.planText().trim());
            q.setDeliveryPlanJson(deliveryJson);
            q.setStatus("LOCKED");
            if (!depositFrozen) {
                fundLedger.freezeDeposit(q, total.multiply(depositRate).setScale(2, java.math.RoundingMode.HALF_UP));
                depositFrozen = true;
            } else {
                q.setDepositStatus("COVERED");
                quotationMapper.updateById(q);
            }
        }
        siteNotify.send(d.getTenantId(), "工厂已填报#" + d.getId(),
                "需求「" + d.getTitle() + "」有工厂完成思考期填报（详情在方案核定期可见）。");
        siteNotify.send(UserContext.tenantId(), "报价已提交#" + d.getId(),
                "你已提交需求「" + d.getTitle() + "」的报价与实施方案，保证金已冻结，意向金已退回。");
        auditService.record("工厂提交报价", "DEMAND", d.getId(), "「" + d.getTitle() + "」保证金已冻结，意向金已退回");
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
        creditScoring.onFactoryThinkingExit(UserContext.tenantId(), demandId);
        siteNotify.send(d.getTenantId(), "有工厂退出#" + demandId,
                "需求「" + d.getTitle() + "」有报名工厂在思考期退出，意向金已退回该厂。");
        siteNotify.send(UserContext.tenantId(), "已取消报价#" + demandId,
                "你已退出需求「" + d.getTitle() + "」的工厂思考期，意向金已退回。");
        auditService.record("工厂思考期退出", "DEMAND", demandId, "「" + d.getTitle() + "」意向金已退回");
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
            notifyIntentionJoined(q);
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
        notifyIntentionJoined(q);
    }

    private void notifyIntentionJoined(Quotation q) {
        Demand d = demandMapper.selectById(q.getDemandId());
        if (d == null) {
            return;
        }
        CoverageView view = coverageService.of(d.getId());
        siteNotify.send(q.getTenantId(), "报名成功#" + d.getId(),
                "你已报名需求「" + d.getTitle() + "」，意向金已冻结。意向期只能报名，思考期开始后才可填报报价。");
        siteNotify.send(d.getTenantId(), "工厂已报名#" + d.getId(),
                "需求「" + d.getTitle() + "」有工厂完成意向报名。" + coverageService.unsatisfiedText(view));
        auditService.record("工厂报名", "DEMAND", d.getId(), "「" + d.getTitle() + "」意向金已冻结");
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
        Demand d = demandMapper.selectById(q.getDemandId());
        if (d != null) {
            CoverageView view = coverageService.of(d.getId());
            siteNotify.send(UserContext.tenantId(), "已取消报名#" + d.getId(),
                    "你已取消需求「" + d.getTitle() + "」的意向报名，意向金已退回。意向期内可重新报名。");
            siteNotify.send(d.getTenantId(), "工厂取消报名#" + d.getId(),
                    "需求「" + d.getTitle() + "」有工厂取消意向报名。" + coverageService.unsatisfiedText(view));
            auditService.record("工厂取消报名", "DEMAND", d.getId(), "「" + d.getTitle() + "」意向金已退回");
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
        siteNotify.send(q.getTenantId(), "已取消锁定报价#" + d.getId(),
                "你已取消需求「" + d.getTitle() + "」的锁定报价，保证金已按规则扣除。");
        siteNotify.send(d.getTenantId(), "工厂取消锁定报价#" + d.getId(),
                "需求「" + d.getTitle() + "」有工厂取消锁定报价。");
        auditService.record("工厂取消锁定报价", "DEMAND", d.getId(), "「" + d.getTitle() + "」保证金已按规则扣除");
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
        Quotation q = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, req.demandId())
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .eq(Quotation::getStatus, "INTENTION")
                .eq(Quotation::getIntentionStatus, "FROZEN")
                .last("limit 1"));
        if (q == null) {
            throw new BizException("请先完成意向报名并支付意向金");
        }
        q.setPrice(req.price());
        q.setYieldRate(d.getMinYield());
        q.setPromisedDays(promisedDaysOf(d));
        q.setMinQty(req.minQty());
        q.setMaxQty(req.maxQty());
        q.setStageCurveJson(req.stageCurveJson());
        q.setStatus("LOCKED");
        quotationMapper.updateById(q);
        fundLedger.freezeDeposit(q, req.price().multiply(depositRate));
        siteNotify.send(UserContext.tenantId(), "已锁定报价#" + d.getId(),
                "你已锁定需求「" + d.getTitle() + "」的报价，保证金已冻结，意向金已退回。");
        siteNotify.send(d.getTenantId(), "工厂已锁定报价#" + d.getId(),
                "需求「" + d.getTitle() + "」有工厂完成锁定报价。");
        return q.getId();
    }

    public List<Quotation> listMine() {
        List<Quotation> list = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .orderByDesc(Quotation::getId));
        Map<Long, Demand> demands = new LinkedHashMap<>();
        Map<Long, Order> orders = new LinkedHashMap<>();
        Map<Long, Contract> contracts = new LinkedHashMap<>();
        for (Quotation q : list) {
            Demand d = demands.computeIfAbsent(q.getDemandId(), demandMapper::selectById);
            q.setDemandTitle(d == null ? "" : d.getTitle());
            if (d != null) {
                q.setDemandStatus(d.getStatus());
            }
            Order o = orders.computeIfAbsent(q.getDemandId(), did -> orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getDemandId, did).orderByDesc(Order::getId).last("limit 1")));
            Contract c = contracts.computeIfAbsent(q.getDemandId(), did -> {
                Order ord = orders.get(did);
                if (ord == null) {
                    return null;
                }
                return contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getOrderId, ord.getId())
                        .eq(Contract::getTenantId, UserContext.tenantId())
                        .last("limit 1"));
            });
            fillStage(q, d, o, c);
        }
        return list;
    }

    private void fillStage(Quotation q, Demand d, Order o, Contract c) {
        if (d == null) {
            q.setActionKey("NONE");
            return;
        }
        String st = d.getStatus();
        LocalDateTime start = stageStartedAt(d, o);
        LocalDateTime end = stageEndedAt(d, start);
        String action = actionOf(q, d, c);

        if ("SOLUTION_SELECTED".equals(st) || "CONTRACTED".equals(st) || "IN_PRODUCTION".equals(st)) {
            // 签约期：进入签约=订单创建；买家下发合同后=附件时间。截止=开始+24h，不因工厂签名后移。
            if (c != null && c.getAttachmentId() != null) {
                Attachment a = attachmentMapper.selectById(c.getAttachmentId());
                if (a != null && a.getCreatedAt() != null) {
                    start = a.getCreatedAt();
                }
                end = start == null ? null : start.plusHours(contractSignHours);
            } else if (o != null && o.getCreatedAt() != null) {
                start = o.getCreatedAt();
                end = start.plusHours(contractSignHours);
            }
            q.setOrderId(o == null ? null : o.getId());
        }
        q.setDemandStageAt(start);
        q.setDemandStageEndAt(end);
        q.setActionKey(action);
    }

    private String actionOf(Quotation q, Demand d, Contract c) {
        String st = d.getStatus();
        if ("PUBLISHED".equals(st)) {
            if ("INVALID".equals(q.getStatus()) || "LOSE".equals(q.getStatus())) {
                return "REAPPLY";
            }
            if ("PENDING_PAY".equals(q.getIntentionStatus())) {
                return "PAY";
            }
            if ("INTENTION".equals(q.getStatus()) && ("FROZEN".equals(q.getIntentionStatus()) || "COVERED".equals(q.getIntentionStatus()))) {
                return "WAIT_INTENTION";
            }
            return "NONE";
        }
        if ("FACTORY_THINKING".equals(st)) {
            if ("INTENTION".equals(q.getStatus())) {
                return "COMMIT";
            }
            if ("LOCKED".equals(q.getStatus())) {
                return "WAIT_BUYER";
            }
            return "NONE";
        }
        if ("BUYER_THINKING".equals(st)) {
            if ("INVALID".equals(q.getStatus()) || "LOSE".equals(q.getStatus())) {
                return "LOSE";
            }
            return "WAIT_BUYER";
        }
        if ("SOLUTION_GENERATED".equals(st)) {
            return "WAIT_SOLUTION";
        }
        if ("FLOW_FAILED".equals(st) || "CANCELLED".equals(st)) {
            return "NONE";
        }
        if ("SOLUTION_CONFIRMED".equals(st) || "SOLUTION_SELECTED".equals(st)
                || "CONTRACTED".equals(st) || "IN_PRODUCTION".equals(st)
                || "COMPLETED".equals(st)) {
            if ("LOSE".equals(q.getStatus()) || "INVALID".equals(q.getStatus())) {
                return "LOSE";
            }
            if (c == null) {
                return "WAIT_ISSUE";
            }
            if ("SIGNED".equals(c.getStatus())) {
                return "SIGNED";
            }
            if (c.getAttachmentId() == null) {
                return "WAIT_ISSUE";
            }
            boolean factorySigned = c.getFactorySign() != null && !c.getFactorySign().isBlank();
            if (factorySigned) {
                return "WAIT_BUYER_CONFIRM";
            }
            return "SIGN";
        }
        return "NONE";
    }

    private LocalDateTime stageStartedAt(Demand d, Order o) {
        String st = d.getStatus();
        if ("PUBLISHED".equals(st) && d.getPublishedAt() != null) {
            return d.getPublishedAt();
        }
        if ("FACTORY_THINKING".equals(st) && d.getFactoryThinkingAt() != null) {
            return d.getFactoryThinkingAt();
        }
        if ("BUYER_THINKING".equals(st) && d.getBuyerThinkingAt() != null) {
            return d.getBuyerThinkingAt();
        }
        if (("SOLUTION_SELECTED".equals(st) || "CONTRACTED".equals(st)) && o != null && o.getCreatedAt() != null) {
            return o.getCreatedAt();
        }
        return d.getUpdatedAt();
    }

    private LocalDateTime stageEndedAt(Demand d, LocalDateTime start) {
        String st = d.getStatus();
        if ("PUBLISHED".equals(st)) {
            return d.getIntentionEndAt();
        }
        if ("FACTORY_THINKING".equals(st)) {
            return d.getFactoryThinkingEndAt() != null ? d.getFactoryThinkingEndAt()
                    : (start == null ? null : start.plusHours(factoryThinkingHours));
        }
        if ("BUYER_THINKING".equals(st)) {
            return d.getBuyerThinkingEndAt() != null ? d.getBuyerThinkingEndAt()
                    : (start == null ? null : start.plusHours(buyerThinkingHours));
        }
        if ("SOLUTION_SELECTED".equals(st) && start != null) {
            return start.plusHours(contractSignHours);
        }
        return null;
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
