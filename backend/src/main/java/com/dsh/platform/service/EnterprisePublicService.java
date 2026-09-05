package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.credit.SurveyScores;
import com.dsh.platform.domain.inspect.InspectPassRate;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.DeviceMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工厂公开画像：买家/运营查看工厂详情用。
 * 历史合格率、完工数等由平台按质检/结算记录计算，工厂不可自改。
 */
@Service
@RequiredArgsConstructor
public class EnterprisePublicService {

    private final EnterpriseMapper enterpriseMapper;
    private final DeviceMapper deviceMapper;
    private final WorkStageMapper workStageMapper;
    private final InspectPassRate inspectPassRate;
    private final SurveyMapper surveyMapper;
    private final CreditEventMapper creditEventMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> publicProfile(Long enterpriseId) {
        Enterprise e = enterpriseMapper.selectById(enterpriseId);
        if (e == null) {
            throw new BizException("企业不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("type", e.getType());
        out.put("name", e.getName());
        out.put("creditScore", e.getCreditScore());
        out.put("authStatus", e.getAuthStatus());
        out.put("address", e.getAddress());
        out.put("introduction", e.getIntroduction());
        out.put("certs", certsOf(e));

        List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, enterpriseId)
                .orderByDesc(Device::getId));
        List<Map<String, Object>> deviceViews = new ArrayList<>();
        int capSum = 0;
        int goodCount = 0;
        int faultCount = 0;
        for (Device d : devices) {
            String st = DeviceService.normalizeStatus(d.getStatus());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.getName());
            m.put("model", d.getModel());
            m.put("precisionText", d.getPrecisionText());
            m.put("processNames", d.getProcessNames());
            m.put("dailyCapacity", d.getDailyCapacity());
            m.put("status", st);
            m.put("createdAt", d.getCreatedAt());
            deviceViews.add(m);
            if ("FAULT".equals(st)) {
                faultCount++;
            } else {
                goodCount++;
                capSum += d.getDailyCapacity() == null ? 0 : d.getDailyCapacity();
            }
        }
        out.put("devices", deviceViews);
        out.put("dailyCapacitySum", capSum);
        out.put("goodDeviceCount", goodCount);
        out.put("faultDeviceCount", faultCount);

        // 平台计算的履约画像
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, enterpriseId));
        long settled = stages.stream().filter(s -> "SETTLED".equals(s.getEscrowStatus())).count();
        out.put("settledStages", settled);
        InspectPassRate.Stats inspectStats = inspectPassRate.ofSettledStages(stages);
        out.put("inspectionCount", inspectStats.judged());
        out.put("inspectionPassRate", inspectStats.percent());
        List<Long> stageIds = stages.stream().map(WorkStage::getId).toList();
        if (!stageIds.isEmpty()) {
            List<Survey> surveys = surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                    .eq(Survey::getRole, "BUYER")
                    .in(Survey::getStageId, stageIds));
            double sum = 0;
            int c = 0;
            for (Survey s : surveys) {
                Double avg = SurveyScores.overallStars(s.getScoresJson(), objectMapper);
                if (avg != null) {
                    sum += avg;
                    c++;
                }
            }
            out.put("surveyAvg", c == 0 ? null : Math.round(sum / c * 10) / 10.0);
        } else {
            out.put("surveyAvg", null);
        }
        List<CreditEvent> events = creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, enterpriseId));
        out.put("noLockCount", events.stream().filter(ev -> "INTENTION_NO_LOCK".equals(ev.getType())).count());
        out.put("penaltyCount", events.stream().filter(ev -> ev.getType() != null
                && (ev.getType().contains("PENALTY") || ev.getType().contains("FORFEIT")
                || ev.getType().contains("VIOLAT"))).count());
        return out;
    }

    /** 工厂自己更新企业介绍。 */
    @Transactional
    public void updateIntroduction(String introduction) {
        Enterprise e = enterpriseMapper.selectById(UserContext.tenantId());
        if (e == null) {
            throw new BizException("企业不存在");
        }
        e.setIntroduction(introduction == null ? "" : introduction.trim());
        enterpriseMapper.updateById(e);
    }

    private List<String> certsOf(Enterprise e) {
        List<String> names = new ArrayList<>();
        if (e.getCapabilityJson() == null || e.getCapabilityJson().isBlank()) {
            return names;
        }
        try {
            JsonNode n = objectMapper.readTree(e.getCapabilityJson()).path("certs");
            if (n.isArray()) {
                n.forEach(x -> names.add(x.asText()));
            }
        } catch (Exception ignored) {
        }
        return names;
    }
}
