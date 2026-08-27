package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowService {

    private final DemandMapper demandMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandStateMachine stateMachine;
    private final ProcessCoverageService coverageService;
    private final SiteNotify siteNotify;
    private final FundLedger fundLedger;
    private final SolutionService solutionService;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;

    @Value("${dsh.time.thinking-hours:12}")
    private int thinkingHours;

    @Value("${dsh.time.review-hours:6}")
    private int reviewHours;

    @Value("${dsh.time.locking-hours:48}")
    private int lockingHours;

    @Transactional
    public void endIntention(Long demandId) {
        Demand d = get(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.PUBLISHED) {
            return;
        }
        stateMachine.transit(d, DemandStatus.THINKING);
        d.setThinkingEndAt(LocalDateTime.now().plusHours(Math.max(thinkingHours, 1)));
        demandMapper.updateById(d);

        CoverageView view = coverageService.of(demandId);
        if (!view.allSatisfied()) {
            siteNotify.send(d.getTenantId(), "意向期结束#" + demandId,
                    "需求「" + d.getTitle() + "」意向期已结束，以下工序产能未满足（不含价格）："
                            + coverageService.unsatisfiedText(view));
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId).eq(Quotation::getStatus, "INTENTION"));
        if (qs.size() > 1) {
            for (Quotation q : qs) {
                siteNotify.send(q.getTenantId(), "竞标提醒",
                        "您参与的「" + d.getTitle() + "」已有其他人报名，请等待后续通知");
            }
        }
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
                "需求「" + d.getTitle() + "」距截止还有 24 小时。当前凑不齐："
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
        stateMachine.transit(d, DemandStatus.CANCELLED);
        demandMapper.updateById(d);
        fundLedger.unfreezeIntentionsOfDemand(d.getId());
        deviceService.releaseByDemand(d.getId());
        siteNotify.send(d.getTenantId(), "需求已取消#" + d.getId(), "需求「" + d.getTitle() + "」已取消，意向金已退还");
    }

    private void notifyFactories(Long demandId, String title, String content) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        for (Quotation q : qs) {
            siteNotify.send(q.getTenantId(), title + "#" + demandId, content);
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
