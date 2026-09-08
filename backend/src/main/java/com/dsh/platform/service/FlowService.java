package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.quote.QuoteEstimate;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowService {

    private final DemandMapper demandMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final CreditScoring creditScoring;
    private final AuditService auditService;
    private final DemandStateMachine stateMachine;
    private final ProcessCoverageService coverageService;
    private final SiteNotify siteNotify;
    private final FundLedger fundLedger;
    private final SolutionService solutionService;
    private final ObjectMapper objectMapper;

    @Value("${dsh.time.thinking-hours:24}")
    private int thinkingHours;

    @Value("${dsh.time.review-hours:6}")
    private int reviewHours;

    @Value("${dsh.time.locking-hours:48}")
    private int lockingHours;

    @Value("${dsh.time.factory-thinking-hours:24}")
    private int factoryThinkingHours;

    @Value("${dsh.time.buyer-thinking-hours:24}")
    private int buyerThinkingHours;

    @Value("${dsh.fee.buyer-deposit-rate:0.05}")
    private BigDecimal buyerDepositRate;

    /** 意向期结束 → 工厂思考期（24h）：报名厂决定是否参加并填报方案。 */
    @Transactional
    public void endIntention(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.PUBLISHED) {
            return;
        }
        stateMachine.transit(d, DemandStatus.FACTORY_THINKING);
        d.setFactoryThinkingAt(LocalDateTime.now());
        d.setFactoryThinkingEndAt(LocalDateTime.now().plusHours(Math.max(factoryThinkingHours, 1)));
        demandMapper.updateById(d);

        CoverageView view = coverageService.of(demandId);
        siteNotify.send(d.getTenantId(), "意向期结束#" + demandId,
                "需求「" + d.getTitle() + "」意向期已结束，进入 " + factoryThinkingHours + " 小时工厂思考期。"
                        + (view.allSatisfied() ? "零件报名产能已满足。"
                        : coverageService.unsatisfiedText(view)));
        notifyFactories(demandId, "工厂思考期开始",
                "需求「" + d.getTitle() + "」进入工厂思考期（" + factoryThinkingHours
                        + " 小时）。参加请填报实施方案、单件报价，并冻结总报价 5% 保证金；"
                        + "不参加可退出并退回意向金。逾期未报价将自动取消报名并退回意向金。");
        auditService.record("结束意向期", "DEMAND", demandId, "进入工厂思考期");
    }

    /** 工厂思考期结束：未报价厂取消报名并退意向金；无论覆盖率如何都进入买家思考期。 */
    @Transactional
    public void endFactoryThinking(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.FACTORY_THINKING) {
            return;
        }
        cancelUnquotedFactories(d);
        List<Quotation> committed = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "LOCKED"));
        BigDecimal estimate = (BigDecimal) QuoteEstimate.of(d, committed).get("estimatedTotal");
        stateMachine.transit(d, DemandStatus.BUYER_THINKING);
        d.setBuyerThinkingAt(LocalDateTime.now());
        d.setBuyerThinkingEndAt(LocalDateTime.now().plusHours(Math.max(buyerThinkingHours, 1)));
        d.setEstimatedTotal(estimate);
        demandMapper.updateById(d);
        BigDecimal deposit = buyerDepositOf(d);
        siteNotify.send(d.getTenantId(), "请决定是否继续#" + demandId,
                "需求「" + d.getTitle() + "」工厂思考期已结束，进入买家思考期（" + buyerThinkingHours
                        + " 小时）。请根据报价自行决定是否继续。预估总价 ¥" + estimate
                        + "，继续需冻结 5% 保证金 ¥" + deposit
                        + "（履约后抵扣尾款）。是否继续由买家自行决定。");
        auditService.record("结束工厂思考期", "DEMAND", demandId,
                "进入买家思考期，预估总价 " + estimate);
    }

    /** 买家思考期决定：CONTINUE 交保证金并触发 AI 方案；CANCEL 结束订单全退意向金与保证金。 */
    @Transactional
    public void buyerDecide(Long demandId, String action, String reason) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.BUYER_THINKING) {
            throw new BizException("当前不是买家思考期");
        }
        if (!UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能操作自己的需求");
        }
        if ("CONTINUE".equals(action)) {
            BigDecimal deposit = buyerDepositOf(d);
            fundLedger.freezeBuyerDeposit(demandId, d.getTenantId(), deposit);
            d.setBuyerDepositStatus("FROZEN");
            stateMachine.transit(d, DemandStatus.SOLUTION_GENERATED);
            demandMapper.updateById(d);
            siteNotify.send(d.getTenantId(), "保证金已冻结#" + demandId,
                    "已冻结保证金 ¥" + deposit + "（履约后抵扣尾款）。各厂报价已可见，AI 正在生成推荐方案，经运营审核后作为参考下发。");
            notifyFactories(demandId, "买家已确认继续#" + demandId,
                    "买家已确认继续需求「" + d.getTitle() + "」，正在生成推荐方案。");
            auditService.record("买家思考期继续", "DEMAND", demandId, "「" + d.getTitle() + "」保证金已冻结，生成方案");
            Long id = d.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    solutionService.aiGenerateFor(id);
                }
            });
            return;
        }
        if ("CANCEL".equals(action)) {
            creditScoring.onBuyerThinkingCancel(d.getTenantId(), d.getId());
            failBuyerThinking(d, DemandStatus.CANCELLED,
                    reason == null || reason.isBlank() ? "买家思考期取消" : reason.trim(),
                    "买家已取消，意向金/保证金已退回。");
            auditService.record("买家思考期取消", "DEMAND", demandId,
                    "「" + d.getTitle() + "」" + (reason == null || reason.isBlank() ? "买家思考期取消" : reason.trim()));
            return;
        }
        throw new BizException("非法操作");
    }

    private void failBuyerThinking(Demand d, DemandStatus target, String reason, String factoryMsg) {
        stateMachine.transit(d, target);
        d.setCancelReason(reason);
        demandMapper.updateById(d);
        siteNotify.send(d.getTenantId(), "订单已结束#" + d.getId(),
                "需求「" + d.getTitle() + "」" + reason + "。");
        notifyFactories(d.getId(), "订单已结束#" + d.getId(),
                "需求「" + d.getTitle() + "」" + factoryMsg);
        fundLedger.unfreezeIntentionsOfDemand(d.getId());
        fundLedger.unfreezeDepositsOfDemand(d.getId());
    }

    /** 思考期结束仍未报价：按取消报名处理，退回意向金，不罚没、不记失信。 */
    private void cancelUnquotedFactories(Demand d) {
        List<Quotation> silent = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "INTENTION"));
        Set<Long> done = new HashSet<>();
        for (Quotation q : silent) {
            fundLedger.unfreezeIntention(q);
            if (!done.add(q.getTenantId())) {
                continue;
            }
            siteNotify.send(q.getTenantId(), "已取消报名#" + d.getId(),
                    "需求「" + d.getTitle() + "」工厂思考期已结束，你未提交报价，报名已自动取消，意向金已退回。");
            siteNotify.send(d.getTenantId(), "工厂取消报名#" + d.getId(),
                    "需求「" + d.getTitle() + "」有工厂因思考期未报价被取消报名，意向金已退回该厂。");
        }
    }

    /** 方案期买家关闭订单：扣除保证金 50% 按各厂承接区间最高值比重赔偿，余款退回。 */
    @Transactional
    public void closeAtSolution(Long demandId, String reason) {
        Demand d = get(demandId);
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st != DemandStatus.SOLUTION_GENERATED && st != DemandStatus.SOLUTION_CONFIRMED) {
            throw new BizException("仅方案期可以关闭订单");
        }
        if (!UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能操作自己的需求");
        }
        List<Quotation> locked = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "LOCKED", "WIN"));
        java.util.Map<Long, Integer> weights = new java.util.LinkedHashMap<>();
        for (Quotation q : locked) {
            if (q.getTenantId() == null) {
                continue;
            }
            int max = Math.max(q.getMaxQty() == null ? 0 : q.getMaxQty(),
                    q.getMinQty() == null ? 0 : q.getMinQty());
            weights.merge(q.getTenantId(), max, Integer::max);
        }
        BigDecimal remain = fundLedger.buyerDepositRemaining(demandId, d.getTenantId());
        BigDecimal pool = remain.multiply(new BigDecimal("0.5")).setScale(2, java.math.RoundingMode.HALF_UP);
        fundLedger.splitBuyerDepositToFactories(demandId, d.getTenantId(), null, pool, weights, "SOLUTION-CLOSE-" + demandId);
        fundLedger.refundBuyerDeposit(demandId, d.getTenantId());
        fundLedger.unfreezeIntentionsOfDemand(demandId);
        fundLedger.unfreezeDepositsOfDemand(demandId);
        stateMachine.transit(d, DemandStatus.CANCELLED);
        d.setCancelReason(reason == null || reason.isBlank() ? "买家方案期关闭订单，保证金 50% 赔偿工厂" : reason.trim());
        demandMapper.updateById(d);
        siteNotify.send(d.getTenantId(), "订单已关闭#" + demandId,
                "需求「" + d.getTitle() + "」已关闭。已扣除保证金 50% 按各厂承接区间最高值比重赔偿，剩余保证金已退回。");
        notifyFactories(demandId, "买家关闭订单#" + demandId,
                "买家已关闭需求「" + d.getTitle() + "」，已按承接区间最高值比重获得赔偿，意向金/保证金余额已退回。");
        auditService.record("方案期关闭订单", "DEMAND", demandId, d.getCancelReason());
    }

    /** 预估总价：去极值后按各厂承接区间最高值加权的单价 × 需求件数。 */
    private BigDecimal estimateTotal(Demand d) {
        List<Quotation> committed = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "LOCKED"));
        Object v = QuoteEstimate.of(d, committed).get("estimatedTotal");
        return v instanceof BigDecimal b ? b : BigDecimal.ZERO;
    }

    private BigDecimal unitPriceOf(Quotation q) {
        if (q.getUnitPrice() != null && q.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return q.getUnitPrice();
        }
        if (q.getPrice() != null && q.getMaxQty() != null && q.getMaxQty() > 0) {
            return q.getPrice().divide(BigDecimal.valueOf(q.getMaxQty()), 2, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }

    public BigDecimal buyerDepositOf(Demand d) {
        BigDecimal base = d.getEstimatedTotal() == null ? BigDecimal.ZERO : d.getEstimatedTotal();
        return base.multiply(buyerDepositRate).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional
    public void notifyLastDay(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.PUBLISHED) {
            return;
        }
        String title = "意向期末日提醒#" + demandId;
        if (siteNotify.exists(d.getTenantId(), title)) {
            return;
        }
        CoverageView view = coverageService.of(demandId);
        if (view.allSatisfied()) {
            return;
        }
        siteNotify.send(d.getTenantId(), title,
                "需求「" + d.getTitle() + "」距截止还有 24 小时。"
                        + coverageService.unsatisfiedText(view));
    }

    @Transactional
    public void decide(Long demandId, String action, String reason) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.THINKING) {
            throw new BizException("当前不是思考期");
        }
        if ("CONTINUE".equals(action)) {
            continueToLocking(d);
            return;
        }
        if ("CANCEL".equals(action)) {
            if (!StringUtils.hasText(reason)) {
                throw new BizException("取消必须填写理由");
            }
            d.setCancelReason(reason.trim());
            boolean noQualified = noQualifiedFactory(d);
            boolean insufficient = !coverageService.of(demandId).allSatisfied();
            if (noQualified || insufficient) {
                cancelAndRefund(d);
            } else {
                stateMachine.transit(d, DemandStatus.REVIEWING);
                d.setReviewEndAt(LocalDateTime.now().plusHours(Math.max(reviewHours, 1)));
                demandMapper.updateById(d);
                siteNotify.send(d.getTenantId(), "取消进入审核#" + demandId,
                        "需求「" + d.getTitle() + "」产能与门槛已满足，取消申请已提交审核。");
                notifyFactories(demandId, "买家申请取消需求#" + demandId,
                        "买家已申请取消需求「" + d.getTitle() + "」，等待平台审核。");
            }
            return;
        }
        throw new BizException("非法操作");
    }

    /** 思考期到期：自动进入保证金期。 */
    @Transactional
    public void endThinking(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.THINKING) {
            return;
        }
        continueToLocking(d);
    }

    @Transactional
    public void reviewCancel(Long demandId, boolean pass) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.REVIEWING) {
            return;
        }
        if (pass) {
            cancelAndRefund(d);
            return;
        }
        rejectCancel(d);
    }

    /** 审核期到期：视为驳回取消，进入保证金期。 */
    @Transactional
    public void endReview(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.REVIEWING) {
            return;
        }
        rejectCancel(d);
    }

    @Transactional
    public void endLocking(Long demandId) {
        solutionService.generate(demandId);
    }

    @Transactional
    public void notifyLockingLastDay(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.LOCKING) {
            return;
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getStatus, "INTENTION"));
        for (Quotation q : qs) {
            String title = "锁价截止提醒#" + demandId;
            if (!siteNotify.exists(q.getTenantId(), title)) {
                siteNotify.send(q.getTenantId(), title,
                        "需求「" + d.getTitle() + "」保证金期将于 24 小时内结束。未锁定报价将扣除意向金并记失信。");
            }
        }
    }

    private void continueToLocking(Demand d) {
        stateMachine.transit(d, DemandStatus.LOCKING);
        d.setLockingEndAt(LocalDateTime.now().plusHours(Math.max(lockingHours, 1)));
        demandMapper.updateById(d);
        notifyFactories(d.getId(), "保证金期已开启", "请在截止前锁定报价，否则将扣除意向金并记失信");
    }

    private void rejectCancel(Demand d) {
        fundLedger.forfeitIntention(d.getId(), d.getTenantId());
        stateMachine.transit(d, DemandStatus.LOCKING);
        d.setLockingEndAt(LocalDateTime.now().plusHours(Math.max(lockingHours, 1)));
        demandMapper.updateById(d);
        siteNotify.send(d.getTenantId(), "取消未通过#" + d.getId(),
                "需求「" + d.getTitle() + "」取消未通过，已扣意向金 500，进入保证金期。");
        notifyFactories(d.getId(), "保证金期已开启", "请前往提交锁定报价");
    }

    private boolean noQualifiedFactory(Demand d) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        for (Quotation q : qs) {
            if (qualified(d, q)) {
                return false;
            }
        }
        return true;
    }

    private boolean qualified(Demand d, Quotation q) {
        Enterprise e = enterpriseMapper.selectById(q.getTenantId());
        BigDecimal yield = q.getYieldRate();
        if (yield == null) {
            yield = snapshotYield(q.getExtraJson());
        }
        if (d.getMinYield() != null && (yield == null || yield.compareTo(d.getMinYield()) < 0)) {
            return false;
        }
        int credit = e == null || e.getCreditScore() == null ? 0 : e.getCreditScore();
        return d.getMinCreditScore() == null || credit >= d.getMinCreditScore();
    }

    private BigDecimal snapshotYield(String extraJson) {
        try {
            if (extraJson == null || extraJson.isBlank()) {
                return null;
            }
            JsonNode n = objectMapper.readTree(extraJson);
            if (n.path("yieldRate").isMissingNode() || n.path("yieldRate").isNull()) {
                return null;
            }
            return BigDecimal.valueOf(n.path("yieldRate").asDouble());
        } catch (Exception e) {
            return null;
        }
    }

    private void cancelAndRefund(Demand d) {
        notifyFactories(d.getId(), "买家取消需求#" + d.getId(),
                "买家已取消需求「" + d.getTitle() + "」，意向金将退回。");
        stateMachine.transit(d, DemandStatus.CANCELLED);
        demandMapper.updateById(d);
        fundLedger.unfreezeIntentionsOfDemand(d.getId());
        siteNotify.send(d.getTenantId(), "需求已取消#" + d.getId(), "需求「" + d.getTitle() + "」已取消，意向金已退还");
    }

    private void notifyFactories(Long demandId, String title, String content) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        String fullTitle = title != null && title.contains("#") ? title : (title + "#" + demandId);
        Set<Long> factories = new HashSet<>();
        for (Quotation q : qs) {
            if (q.getTenantId() != null) {
                factories.add(q.getTenantId());
            }
        }
        for (Long factoryId : factories) {
            if (siteNotify.exists(factoryId, fullTitle)) {
                continue;
            }
            siteNotify.send(factoryId, fullTitle, content);
        }
    }

    private Demand get(Long id) {
        Demand d = demandMapper.selectById(id);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        return d;
    }
}
