package com.dsh.platform.domain.inspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.InspectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检合格率：只统计已结算工单；每单取最近一次 PASS/FAIL；未质检不计入分母。
 */
@Component
@RequiredArgsConstructor
public class InspectPassRate {

    private final InspectionMapper inspectionMapper;

    public record Stats(long judged, long pass, Double percent) {
    }

    public Stats ofSettledStages(List<WorkStage> stages) {
        List<Long> settledIds = stages == null ? List.of() : stages.stream()
                .filter(s -> s != null && "SETTLED".equals(s.getEscrowStatus()) && s.getId() != null)
                .map(WorkStage::getId)
                .distinct()
                .toList();
        if (settledIds.isEmpty()) {
            return new Stats(0, 0, null);
        }
        List<Inspection> ins = inspectionMapper.selectList(new LambdaQueryWrapper<Inspection>()
                .in(Inspection::getStageId, settledIds)
                .in(Inspection::getResult, "PASS", "FAIL")
                .orderByAsc(Inspection::getId));
        Map<Long, String> latest = new LinkedHashMap<>();
        for (Inspection i : ins) {
            if (i.getStageId() != null) {
                latest.put(i.getStageId(), i.getResult());
            }
        }
        long judged = latest.size();
        long pass = latest.values().stream().filter("PASS"::equals).count();
        Double percent = judged == 0 ? null : Math.round(pass * 1000.0 / judged) / 10.0;
        return new Stats(judged, pass, percent);
    }
}
