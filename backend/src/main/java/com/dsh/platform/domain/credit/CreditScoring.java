package com.dsh.platform.domain.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工厂信誉 = 质量分×35% + 守时率×25% + 合作分×25% + 守信分×15%。
 * 买家信誉 = 付款率×40% + 守信分×35% + 合作分×25%。
 */
@Component
@RequiredArgsConstructor
public class CreditScoring {

    public static final String QUALITY_PASS = "QUALITY_PASS";
    public static final String QUALITY_FAIL = "QUALITY_FAIL";
    public static final String QUALITY_QTY_FAIL = "QUALITY_QTY_FAIL";
    public static final String QUALITY_STD_FAIL = "QUALITY_STD_FAIL";
    public static final String PUNCTUAL_ON = "PUNCTUAL_ON";
    public static final String PUNCTUAL_LATE = "PUNCTUAL_LATE";
    public static final String HONESTY_FACTORY_EXIT = "HONESTY_FACTORY_EXIT";
    public static final String HONESTY_OVERDUE = "HONESTY_OVERDUE";
    public static final String HONESTY_INSPECT_FAIL = "HONESTY_INSPECT_FAIL";
    public static final String HONESTY_CLEAN_ORDER = "HONESTY_CLEAN_ORDER";
    public static final String BUYER_THINKING_CANCEL = "BUYER_THINKING_CANCEL";
    public static final String STAGE_PASS = "STAGE_PASS";
    public static final String PAY_ON_TIME = "PAY_ON_TIME";
    public static final String PAY_LATE = "PAY_LATE";

    private static final int SURVEY_N = 10;
    private static final int PAY_HOURS = 48;

    private final WorkStageMapper workStageMapper;
    private final SurveyMapper surveyMapper;
    private final CreditEventMapper creditEventMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public void applyNoLock(Long tenantId, Long demandId) {
        // 意向期不再报价，未锁价不再记失信。
    }

    public void onFactoryThinkingExit(Long factoryId, Long demandId) {
        onFactoryThinkingExit(factoryId, demandId, "工厂思考期退出");
    }

    public void onFactoryThinkingExit(Long factoryId, Long demandId, String remark) {
        insert(factoryId, HONESTY_FACTORY_EXIT, -5, "DEMAND", demandId,
                remark == null || remark.isBlank() ? "工厂思考期退出" : remark);
        refresh(factoryId);
    }

    public void onBuyerThinkingCancel(Long buyerId, Long demandId) {
        onBuyerThinkingCancel(buyerId, demandId, "买家思考期取消");
    }

    public void onBuyerThinkingCancel(Long buyerId, Long demandId, String remark) {
        insert(buyerId, BUYER_THINKING_CANCEL, -10, "DEMAND", demandId,
                remark == null || remark.isBlank() ? "买家思考期取消" : remark);
        refresh(buyerId);
    }

    public void onDeliver(WorkStage ws) {
        if (ws == null || ws.getTenantId() == null) {
            return;
        }
        boolean overdue = overdue(ws, LocalDateTime.now());
        if (overdue) {
            insert(ws.getTenantId(), PUNCTUAL_LATE, -1, "STAGE", ws.getId(), "逾期交付");
            insert(ws.getTenantId(), HONESTY_OVERDUE, -5, "STAGE", ws.getId(), "逾期未交付");
        } else {
            insert(ws.getTenantId(), PUNCTUAL_ON, 1, "STAGE", ws.getId(), "按时交付");
        }
        refresh(ws.getTenantId());
    }

    public void onInspectResult(WorkStage ws, boolean pass, Long inspectionId) {
        onInspectResult(ws, pass, inspectionId, true, pass);
    }

    public void onInspectResult(WorkStage ws, boolean pass, Long inspectionId,
                                 boolean quantityOk, boolean toleranceOk) {
        if (ws == null || ws.getTenantId() == null) {
            return;
        }
        Long ref = inspectionId != null ? inspectionId : ws.getId();
        if (pass) {
            insert(ws.getTenantId(), QUALITY_PASS, 2, "INSPECTION", ref, "质检合格");
        } else {
            if (!quantityOk) {
                insert(ws.getTenantId(), QUALITY_QTY_FAIL, -3, "INSPECTION", ref, "交货数量不足");
            }
            if (!toleranceOk) {
                insert(ws.getTenantId(), QUALITY_STD_FAIL, -3, "INSPECTION", ref, "质量不达标");
            }
        }
        refresh(ws.getTenantId());
    }

    /** 让步后工单变为可收款，只开付款窗口，不记信用事件。 */
    public void onConcessionPass(WorkStage ws) {
        // 付款是否及时改用工单进入可收款的时间（updatedAt）
    }

    public void onPaid(WorkStage ws, Long buyerId) {
        if (ws == null || buyerId == null) {
            return;
        }
        LocalDateTime passAt = eventTime(ws.getTenantId(), STAGE_PASS, "STAGE", ws.getId());
        if (passAt == null) {
            passAt = ws.getUpdatedAt() == null ? LocalDateTime.now() : ws.getUpdatedAt();
        }
        boolean onTime = !LocalDateTime.now().isAfter(passAt.plusHours(PAY_HOURS));
        if (onTime) {
            insert(buyerId, PAY_ON_TIME, 1, "STAGE", ws.getId(), "质检通过后 48 小时内付款");
        } else {
            insert(buyerId, PAY_LATE, -1, "STAGE", ws.getId(), "质检通过后超过 48 小时付款");
        }
        refresh(buyerId);
    }

    public void onSurveySubmitted(WorkStage ws, Long buyerId) {
        if (ws != null) {
            refresh(ws.getTenantId());
        }
        refresh(buyerId);
    }

    public void applyOnComplete(Order order) {
        if (order == null) {
            return;
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, order.getId()));
        Set<Long> factories = stages.stream().map(WorkStage::getTenantId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LocalDateTime since = order.getCreatedAt() == null ? LocalDateTime.now().minusYears(1) : order.getCreatedAt();
        for (Long factoryId : factories) {
            boolean dirty = eventsOf(factoryId).stream().anyMatch(ev -> {
                if (ev.getCreatedAt() != null && ev.getCreatedAt().isBefore(since)) {
                    return false;
                }
                return HONESTY_OVERDUE.equals(ev.getType())
                        || HONESTY_INSPECT_FAIL.equals(ev.getType())
                        || QUALITY_FAIL.equals(ev.getType())
                        || QUALITY_QTY_FAIL.equals(ev.getType())
                        || QUALITY_STD_FAIL.equals(ev.getType());
            });
            if (!dirty) {
                insert(factoryId, HONESTY_CLEAN_ORDER, 2, "ORDER", order.getId(), "整单完工且无失信");
            }
            refresh(factoryId);
        }
        Demand d = demandMapper.selectById(order.getDemandId());
        if (d != null) {
            refresh(d.getTenantId());
        }
    }

    public void refresh(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        Enterprise ent = enterpriseMapper.selectById(tenantId);
        if (ent == null) {
            return;
        }
        int score = "BUYER".equalsIgnoreCase(ent.getType()) ? buyerScore(ent.getId()) : factoryScore(ent.getId());
        ent.setCreditScore(clamp(score));
        enterpriseMapper.updateById(ent);
    }

    private int factoryScore(Long factoryId) {
        List<CreditEvent> events = eventsOf(factoryId);
        double quality = qualityScore(events);
        double punctual = punctualRate(events);
        double coop = cooperation(factoryId, "BUYER");
        double honesty = factoryHonesty(events);
        return (int) Math.round(quality * 0.35 + punctual * 0.25 + coop * 0.25 + honesty * 0.15);
    }

    private int buyerScore(Long buyerId) {
        List<CreditEvent> events = eventsOf(buyerId);
        double pay = paymentRate(buyerId, events);
        double honesty = buyerHonesty(events);
        double coop = cooperationForBuyer(buyerId);
        return (int) Math.round(pay * 0.40 + honesty * 0.35 + coop * 0.25);
    }

    private double qualityScore(List<CreditEvent> events) {
        double acc = 0;
        LocalDateTime now = LocalDateTime.now();
        for (CreditEvent ev : events) {
            if (QUALITY_PASS.equals(ev.getType())) {
                acc += 2 * qualityWeight(ev.getCreatedAt(), now);
            } else if (QUALITY_FAIL.equals(ev.getType())) {
                acc += -8 * qualityWeight(ev.getCreatedAt(), now);
            } else if (QUALITY_QTY_FAIL.equals(ev.getType()) || QUALITY_STD_FAIL.equals(ev.getType())) {
                acc += -3 * qualityWeight(ev.getCreatedAt(), now);
            }
        }
        return clamp(100 + acc);
    }

    private static double qualityWeight(LocalDateTime at, LocalDateTime now) {
        if (at == null) {
            return 1;
        }
        long months = Duration.between(at, now).toDays() / 30;
        if (months <= 6) {
            return 1;
        }
        if (months <= 12) {
            return 0.5;
        }
        return 0;
    }

    private double punctualRate(List<CreditEvent> events) {
        LocalDateTime now = LocalDateTime.now();
        double on = 0;
        double total = 0;
        for (CreditEvent ev : events) {
            if (!PUNCTUAL_ON.equals(ev.getType()) && !PUNCTUAL_LATE.equals(ev.getType())) {
                continue;
            }
            double w = generalWeight(ev.getCreatedAt(), now);
            total += w;
            if (PUNCTUAL_ON.equals(ev.getType())) {
                on += w;
            }
        }
        return total <= 0 ? 100 : clamp(on / total * 100);
    }

    private double factoryHonesty(List<CreditEvent> events) {
        int score = 100;
        for (CreditEvent ev : events) {
            if (HONESTY_FACTORY_EXIT.equals(ev.getType())
                    || HONESTY_OVERDUE.equals(ev.getType())
                    || HONESTY_INSPECT_FAIL.equals(ev.getType())
                    || HONESTY_CLEAN_ORDER.equals(ev.getType())) {
                score += ev.getScoreChange() == null ? 0 : ev.getScoreChange();
            }
        }
        return clamp(score);
    }

    private double buyerHonesty(List<CreditEvent> events) {
        int score = 100;
        for (CreditEvent ev : events) {
            if (BUYER_THINKING_CANCEL.equals(ev.getType())) {
                score += ev.getScoreChange() == null ? 0 : ev.getScoreChange();
            }
        }
        return clamp(score);
    }

    private double paymentRate(Long buyerId, List<CreditEvent> events) {
        LocalDateTime now = LocalDateTime.now();
        double on = 0;
        double total = 0;
        for (CreditEvent ev : events) {
            if (!PAY_ON_TIME.equals(ev.getType()) && !PAY_LATE.equals(ev.getType())) {
                continue;
            }
            double w = generalWeight(ev.getCreatedAt(), now);
            total += w;
            if (PAY_ON_TIME.equals(ev.getType())) {
                on += w;
            }
        }
        for (WorkStage ws : stagesOfBuyer(buyerId)) {
            if (!"PASS".equals(ws.getStatus())) {
                continue;
            }
            if (ws.getPayAmount() == null || ws.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean paid = "HELD".equals(ws.getEscrowStatus()) || "SETTLED".equals(ws.getEscrowStatus());
            if (hasEvent(buyerId, PAY_ON_TIME, "STAGE", ws.getId()) || hasEvent(buyerId, PAY_LATE, "STAGE", ws.getId())) {
                continue;
            }
            LocalDateTime passAt = eventTime(ws.getTenantId(), STAGE_PASS, "STAGE", ws.getId());
            if (passAt == null) {
                passAt = ws.getUpdatedAt() == null ? now : ws.getUpdatedAt();
            }
            if (!paid && !now.isAfter(passAt.plusHours(PAY_HOURS))) {
                continue;
            }
            double w = generalWeight(passAt, now);
            total += w;
            if (paid && !now.isAfter(passAt.plusHours(PAY_HOURS))) {
                on += w;
            }
        }
        return total <= 0 ? 100 : clamp(on / total * 100);
    }

    private double cooperation(Long factoryId, String raterRole) {
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, factoryId));
        List<Long> ids = stages.stream().map(WorkStage::getId).toList();
        if (ids.isEmpty()) {
            return 80;
        }
        List<Survey> surveys = surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                .eq(Survey::getRole, raterRole)
                .in(Survey::getStageId, ids)
                .orderByDesc(Survey::getId));
        return avgStarsToScore(surveys);
    }

    private double cooperationForBuyer(Long buyerId) {
        List<WorkStage> stages = stagesOfBuyer(buyerId);
        List<Long> ids = stages.stream().map(WorkStage::getId).toList();
        if (ids.isEmpty()) {
            return 80;
        }
        List<Survey> surveys = surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                .eq(Survey::getRole, "FACTORY")
                .in(Survey::getStageId, ids)
                .orderByDesc(Survey::getId));
        return avgStarsToScore(surveys);
    }

    private double avgStarsToScore(List<Survey> surveys) {
        List<Double> stars = new ArrayList<>();
        for (Survey s : surveys) {
            Double v = SurveyScores.overallStars(s.getScoresJson(), objectMapper);
            if (v != null) {
                stars.add(v);
            }
            if (stars.size() >= SURVEY_N) {
                break;
            }
        }
        if (stars.isEmpty()) {
            return 80;
        }
        double avg = stars.stream().mapToDouble(x -> x).average().orElse(4);
        return clamp(avg * 20);
    }

    private List<WorkStage> stagesOfBuyer(Long buyerId) {
        List<Demand> demands = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, buyerId));
        if (demands.isEmpty()) {
            return List.of();
        }
        List<Long> demandIds = demands.stream().map(Demand::getId).toList();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getDemandId, demandIds));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .in(WorkStage::getOrderId, orderIds));
    }

    private static double generalWeight(LocalDateTime at, LocalDateTime now) {
        if (at == null) {
            return 1;
        }
        long days = Duration.between(at, now).toDays();
        if (days <= 30) {
            return 1.0;
        }
        if (days <= 90) {
            return 0.7;
        }
        if (days <= 180) {
            return 0.5;
        }
        return 0.25;
    }

    private static boolean overdue(WorkStage ws, LocalDateTime now) {
        LocalDateTime due = ws.getPeriodEnd();
        if (due == null && ws.getPromisedDate() != null) {
            due = ws.getPromisedDate().atTime(23, 59, 59);
        }
        if (due == null && ws.getPeriodStart() != null && ws.getPromisedDays() != null) {
            due = ws.getPeriodStart().plusDays(ws.getPromisedDays());
        }
        return due != null && now.isAfter(due);
    }

    private List<CreditEvent> eventsOf(Long tenantId) {
        return creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId)
                .orderByAsc(CreditEvent::getId));
    }

    private boolean hasEvent(Long tenantId, String type, String refType, Long refId) {
        if (tenantId == null || refId == null) {
            return false;
        }
        Long n = creditEventMapper.selectCount(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId)
                .eq(CreditEvent::getType, type)
                .eq(CreditEvent::getRefType, refType)
                .eq(CreditEvent::getRefId, refId));
        return n != null && n > 0;
    }

    private LocalDateTime eventTime(Long tenantId, String type, String refType, Long refId) {
        CreditEvent ev = creditEventMapper.selectOne(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId)
                .eq(CreditEvent::getType, type)
                .eq(CreditEvent::getRefType, refType)
                .eq(CreditEvent::getRefId, refId)
                .orderByAsc(CreditEvent::getId)
                .last("limit 1"));
        return ev == null ? null : ev.getCreatedAt();
    }

    private void insert(Long tenantId, String type, int change, String refType, Long refId, String remark) {
        if (change == 0) {
            return;
        }
        if (tenantId == null || refId == null || hasEvent(tenantId, type, refType, refId)) {
            return;
        }
        CreditEvent ev = new CreditEvent();
        ev.setTenantId(tenantId);
        ev.setType(type);
        ev.setScoreChange(change);
        ev.setRefType(refType);
        ev.setRefId(refId);
        ev.setRemark(remark);
        ev.setCreatedAt(LocalDateTime.now());
        creditEventMapper.insert(ev);
    }

    private static int clamp(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }
}
