package com.dsh.platform.domain.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 完工后按问卷均分、完工率、合格率改信用分。
 */
@Component
@RequiredArgsConstructor
public class CreditScoring {

    private final WorkStageMapper workStageMapper;
    private final SurveyMapper surveyMapper;
    private final CreditEventMapper creditEventMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;

    @Value("${dsh.credit.survey-weight:0.4}")
    private double surveyWeight;
    @Value("${dsh.credit.complete-weight:0.3}")
    private double completeWeight;
    @Value("${dsh.credit.pass-weight:0.3}")
    private double passWeight;

    public void applyNoLock(Long tenantId, Long demandId) {
        if (tenantId == null) {
            return;
        }
        CreditEvent ev = new CreditEvent();
        ev.setTenantId(tenantId);
        ev.setType("INTENTION_NO_LOCK");
        ev.setScoreChange(-5);
        ev.setRefType("DEMAND");
        ev.setRefId(demandId);
        ev.setRemark("保证金期截止未锁定报价，扣除意向金并记失信");
        creditEventMapper.insert(ev);
        Enterprise ent = enterpriseMapper.selectById(tenantId);
        if (ent == null) {
            return;
        }
        int base = ent.getCreditScore() == null ? 60 : ent.getCreditScore();
        ent.setCreditScore(Math.max(0, Math.min(100, base - 5)));
        enterpriseMapper.updateById(ent);
    }

    public void applyOnComplete(Order order) {
        if (order == null) {
            return;
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, order.getId()));
        if (stages.isEmpty()) {
            return;
        }
        Map<Long, List<WorkStage>> byFactory = stages.stream()
                .collect(Collectors.groupingBy(WorkStage::getTenantId));
        List<Survey> surveys = surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                .eq(Survey::getOrderId, order.getId()));
        for (Map.Entry<Long, List<WorkStage>> e : byFactory.entrySet()) {
            scoreFactory(order, e.getKey(), e.getValue(), surveys);
        }
    }

    private void scoreFactory(Order order, Long factoryId, List<WorkStage> stages, List<Survey> all) {
        long pass = stages.stream().filter(s -> "PASS".equals(s.getStatus())).count();
        double passRate = stages.isEmpty() ? 0 : (double) pass / stages.size();
        List<Long> stageIds = stages.stream().map(WorkStage::getId).toList();
        List<Survey> related = all.stream().filter(s -> stageIds.contains(s.getStageId())).toList();
        double surveyNorm = related.isEmpty() ? 0.6 : averageScore(related) / 5.0;
        double composite = surveyNorm * surveyWeight + 1.0 * completeWeight + passRate * passWeight;
        int delta = (int) Math.round((composite - 0.6) * 20);
        if (delta == 0 && !related.isEmpty()) {
            delta = 1;
        }
        CreditEvent ev = new CreditEvent();
        ev.setTenantId(factoryId);
        ev.setType("ORDER_COMPLETE");
        ev.setScoreChange(delta);
        ev.setRefType("ORDER");
        ev.setRefId(order.getId());
        ev.setRemark("问卷均分/完工/合格率加权");
        creditEventMapper.insert(ev);

        Enterprise ent = enterpriseMapper.selectById(factoryId);
        if (ent == null) {
            return;
        }
        int base = ent.getCreditScore() == null ? 60 : ent.getCreditScore();
        ent.setCreditScore(Math.max(0, Math.min(100, base + delta)));
        enterpriseMapper.updateById(ent);
    }

    private double averageScore(List<Survey> surveys) {
        double sum = 0;
        int n = 0;
        for (Survey s : surveys) {
            try {
                JsonNode node = objectMapper.readTree(s.getScoresJson());
                for (String k : List.of("q1", "q2", "q3", "q4")) {
                    if (node.has(k)) {
                        sum += node.get(k).asInt(0);
                        n++;
                    }
                }
            } catch (Exception ignored) {
                // 单份坏数据跳过
            }
        }
        return n == 0 ? 3 : sum / n;
    }
}
