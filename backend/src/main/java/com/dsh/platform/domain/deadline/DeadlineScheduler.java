package com.dsh.platform.domain.deadline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.service.FlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 只负责到期调用状态机/流程，不写业务细则。运营手点走同一套 FlowService.endXxx。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineScheduler {

    private final DemandMapper demandMapper;
    private final FlowService flowService;
    private final WorkStageMapper workStageMapper;
    private final OrderMapper orderMapper;
    private final SiteNotify siteNotify;

    @Scheduled(fixedDelay = 60000)
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<Demand> published = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.PUBLISHED.name())
                .isNotNull(Demand::getIntentionEndAt));
        for (Demand d : published) {
            try {
                LocalDateTime end = d.getIntentionEndAt();
                LocalDateTime remindAt = end.minusDays(1);
                if (!now.isBefore(remindAt) && now.isBefore(end)) {
                    flowService.notifyLastDay(d.getId());
                }
                if (!end.isAfter(now)) {
                    flowService.endIntention(d.getId());
                }
            } catch (Exception e) {
                log.warn("意向期调度失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
        for (Demand d : demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.FACTORY_THINKING.name())
                .isNotNull(Demand::getFactoryThinkingEndAt)
                .le(Demand::getFactoryThinkingEndAt, now))) {
            try {
                flowService.endFactoryThinking(d.getId());
            } catch (Exception e) {
                log.warn("工厂思考期到期处理失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
        for (Demand d : demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.BUYER_THINKING.name())
                .isNotNull(Demand::getBuyerThinkingEndAt)
                .le(Demand::getBuyerThinkingEndAt, now))) {
            try {
                flowService.timeoutBuyerThinking(d.getId());
            } catch (Exception e) {
                log.warn("买家思考期超时处理失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
        for (Demand d : demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.THINKING.name())
                .isNotNull(Demand::getThinkingEndAt)
                .le(Demand::getThinkingEndAt, now))) {
            try {
                flowService.timeoutThinking(d.getId());
            } catch (Exception e) {
                log.warn("思考期超时失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
        for (Demand d : demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.REVIEWING.name())
                .isNotNull(Demand::getReviewEndAt)
                .le(Demand::getReviewEndAt, now))) {
            try {
                flowService.timeoutReview(d.getId());
            } catch (Exception e) {
                log.warn("审核期超时失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
        List<Demand> locking = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.LOCKING.name())
                .isNotNull(Demand::getLockingEndAt));
        for (Demand d : locking) {
            try {
                LocalDateTime end = d.getLockingEndAt();
                LocalDateTime remindAt = end.minusHours(24);
                if (!now.isBefore(remindAt) && now.isBefore(end)) {
                    flowService.notifyLockingLastDay(d.getId());
                }
                if (!end.isAfter(now)) {
                    flowService.endLocking(d.getId());
                }
            } catch (Exception e) {
                log.warn("保证金期调度失败 demandId={}: {}", d.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void overdueStages() {
        LocalDate today = LocalDate.now();
        List<WorkStage> list = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .in(WorkStage::getStatus, "PENDING", "IN_PRODUCTION")
                .isNotNull(WorkStage::getPromisedDate)
                .lt(WorkStage::getPromisedDate, today));
        for (WorkStage ws : list) {
            try {
                String title = "工单逾期#" + ws.getId();
                long days = ChronoUnit.DAYS.between(ws.getPromisedDate(), today);
                String content = "工序「" + ws.getProcessName() + "」承诺交期 "
                        + ws.getPromisedDate() + "，已逾期 " + days + " 天。";
                if (!siteNotify.exists(ws.getTenantId(), title)) {
                    siteNotify.send(ws.getTenantId(), title, content);
                }
                Order o = orderMapper.selectById(ws.getOrderId());
                Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
                if (d != null && !siteNotify.exists(d.getTenantId(), title)) {
                    siteNotify.send(d.getTenantId(), title, content);
                }
            } catch (Exception e) {
                log.warn("逾期通知失败 stageId={}: {}", ws.getId(), e.getMessage());
            }
        }
    }
}
