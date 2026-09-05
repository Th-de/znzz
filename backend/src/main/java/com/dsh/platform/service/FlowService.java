package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
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
                        + " 小时）。参加请填报实施方案、单件报价和分期交付内容，并冻结总报价 5% 保证金；"
                        + "不参加可退出并退回意向金。逾期未填报将扣除意向金并记失信。");
        auditService.record("结束意向期", "DEMAND", demandId, "进入工厂思考期");
    }

    /** 工厂思考期结束：填报覆盖则进买家思考期，否则流拍全退。 */
    @Transactional
    public void endFactoryThinking(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.FACTORY_THINKING) {
            return;
        }
        deductFactoryThinkingTimeout(d);
        if (!coverageService.committedPiecesCovered(d.getId())) {
            stateMachine.transit(d, DemandStatus.FLOW_FAILED);
            demandMapper.updateById(d);
            siteNotify.send(d.getTenantId(), "需求流拍#" + demandId,
                    "需求「" + d.getTitle() + "」工厂思考期结束后各工序填报产能不足，已流拍，相关资金已退回。");
            notifyFactories(demandId, "需求流拍#" + demandId,
                    "需求「" + d.getTitle() + "」已流拍，意向金/保证金已退回。");
            fundLedger.unfreezeIntentionsOfDemand(demandId);
            fundLedger.unfreezeDepositsOfDemand(demandId);
            return;
        }
        forfeitSilentFactories(d);
        BigDecimal estimate = estimateTotal(d);
        stateMachine.transit(d, DemandStatus.BUYER_THINKING);
        d.setBuyerThinkingAt(LocalDateTime.now());
        d.setBuyerThinkingEndAt(LocalDateTime.now().plusHours(Math.max(buyerThinkingHours, 1)));
        d.setEstimatedTotal(estimate);
        demandMapper.updateById(d);
        BigDecimal deposit = buyerDepositOf(d);
        siteNotify.send(d.getTenantId(), "请决定是否继续#" + demandId,
                "需求「" + d.getTitle() + "」工厂填报已完成，进入买家思考期（" + buyerThinkingHours
                        + " 小时）。AI 预估总价 ¥" + estimate + "，继续需冻结 5% 保证金 ¥" + deposit
                        + "（履约后抵扣尾款）；取消或超时未操作将结束订单并退回各方资金。");
        auditService.record("结束工厂思考期", "DEMAND", demandId,
                "进入买家思考期，预估总价 " + estimate);
    }

    /** 买家思考期决定：CONTINUE 交保证金并触发 AI 方案；CANCEL 结束订单全退。 */
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
            return;
        }
        throw new BizException("非法操作");
    }

    /** 买家思考期超时未交保证金：自动流单，全退。 */
    @Transactional
    public void timeoutBuyerThinking(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.BUYER_THINKING) {
            return;
        }
        creditScoring.onBuyerThinkingCancel(d.getTenantId(), d.getId(), "买家思考期超时自动取消");
        failBuyerThinking(d, DemandStatus.FLOW_FAILED, "买家思考期超时未交保证金，自动流单并扣守信分",
                "买家超时未交保证金，订单已流单，意向金/保证金已退回。");
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

    /** 工厂思考期截止仍未填报：按退出同等扣守信分 5 分（按厂去重）。 */
    private void deductFactoryThinkingTimeout(Demand d) {
        List<Quotation> silent = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "INTENTION"));
        Set<Long> done = new HashSet<>();
        for (Quotation q : silent) {
            if (!done.add(q.getTenantId())) {
                continue;
            }
            creditScoring.onFactoryThinkingExit(q.getTenantId(), d.getId(), "工厂思考期超时未填报");
            siteNotify.send(q.getTenantId(), "思考期超时扣分#" + d.getId(),
                    "需求「" + d.getTitle() + "」工厂思考期已截止，你未填报也未退出，守信分 −5。");
        }
    }

    /** 逾期既不填报也不退出的厂：扣意向金（守信分已在到期时记过）。 */
    private void forfeitSilentFactories(Demand d) {
        Set<Long> done = new HashSet<>();
        for (Quotation q : fundLedger.forfeitUnlockedIntentions(d.getId())) {
            if (!done.add(q.getTenantId())) {
                continue;
            }
            siteNotify.send(q.getTenantId(), "未填报扣除意向金#" + d.getId(),
                    "需求「" + d.getTitle() + "」工厂思考期已结束，你未填报方案也未退出，意向金已扣除并扣守信分。");
        }
    }

    /** 预估总价：该品最低单价 × 需求件数（工序不再拆数量）。 */
    private BigDecimal estimateTotal(Demand d) {
        List<Quotation> committed = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "LOCKED"));
        BigDecimal minUnit = committed.stream()
                .map(this::unitPriceOf)
                .filter(u -> u != null && u.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int qty = d.getQuantity() == null ? 0 : d.getQuantity();
        return minUnit.multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP);
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

    @Transactional
    public void timeoutThinking(Long demandId) {
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

    @Transactional
    public void timeoutReview(Long demandId) {
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
