package com.dsh.platform.domain.solution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.InspectionMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出 2～3 套综合方案。厂和价必须落在已锁定报价里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSolutionGenerator {

    private final ProcessMapper processMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final CreditEventMapper creditEventMapper;
    private final WorkStageMapper workStageMapper;
    private final InspectionMapper inspectionMapper;
    private final SurveyMapper surveyMapper;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    @Value("${dsh.ai.api-key:}")
    private String apiKey;
    @Value("${dsh.ai.model:deepseek-v4-pro}")
    private String model;
    @Value("${dsh.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<SolutionCombo> generate(Demand demand) {
        if (!configured()) {
            throw new BizException("未配置 AI 密钥，规则方案仍可用");
        }
        List<Process> processes = processMapper.selectList(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, demand.getId())
                .orderByAsc(Process::getProcessNo));
        if (processes.isEmpty()) {
            Process one = new Process();
            one.setProcessNo(1);
            one.setProcessName("整单");
            one.setQuantity(demand.getQuantity());
            processes = List.of(one);
        }
        List<Quotation> locked = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demand.getId())
                .eq(Quotation::getStatus, "LOCKED"));
        if (locked.isEmpty()) {
            throw new BizException("没有锁定报价，无法生成 AI 方案");
        }
        String content = callModel(buildPrompt(demand, processes, locked));
        List<SolutionCombo> parsed = parseAndHydrate(content, processes, locked);
        if (parsed.isEmpty()) {
            throw new BizException("AI 方案无法落到已锁定报价，已放弃");
        }
        return parsed;
    }

    private String buildPrompt(Demand demand, List<Process> processes, List<Quotation> locked) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是制造业订单评审专家。综合产能可行性、历史履约记录、信用状况、质量表现、价格与工期，");
        sb.append("为每道工序选出最合适的工厂，产出一份完整的履约方案。\n");
        sb.append("只能从候选工厂里选，不能发明工厂或价格。只返回 JSON。\n");
        sb.append("产能核算：需要的日产出 ≈ 工序数量 ÷ 承诺工期（天）。对比该厂勾选设备的日产能合计，判断是否可行、余量多少。\n");
        sb.append("给出 1 套主方案，最多再给 1 套备选。\n\n");
        sb.append("需求：").append(demand.getTitle()).append(" / ").append(demand.getProductName()).append('\n');
        sb.append("数量：").append(demand.getQuantity())
                .append(" 材料：").append(nvl(demand.getMaterial()))
                .append(" 公差：").append(nvl(demand.getTolerance()))
                .append(" AQL：").append(nvl(demand.getAql()))
                .append(" 认证：").append(nvl(demand.getCertification()))
                .append(" 最低良率：").append(demand.getMinYield())
                .append(" 最低信用：").append(demand.getMinCreditScore())
                .append(" 硬交期：").append(demand.getDeadlineHard())
                .append('\n');
        if (demand.getRemark() != null && !demand.getRemark().isBlank()) {
            sb.append("备注：").append(demand.getRemark()).append('\n');
        }
        sb.append("工序：\n");
        for (Process p : processes) {
            sb.append("- processNo=").append(p.getProcessNo())
                    .append(" name=").append(p.getProcessName())
                    .append(" qty=").append(p.getQuantity())
                    .append(" req=").append(nvl(p.getRequirement())).append('\n');
        }
        sb.append("候选锁定报价与工厂画像：\n");
        for (Quotation q : locked) {
            Enterprise e = enterpriseMapper.selectById(q.getTenantId());
            List<Device> devices = deviceService.byIds(deviceService.parseIds(q.getDeviceIdsJson()));
            int cap = deviceService.dailyCapacitySum(devices);
            sb.append("- factoryId=").append(q.getTenantId())
                    .append(" factoryName=").append(e == null ? "" : e.getName())
                    .append(" credit=").append(e == null ? "" : e.getCreditScore())
                    .append(" auth=").append(e == null ? "" : e.getAuthStatus())
                    .append(" processNo=").append(q.getProcessNo())
                    .append(" price=").append(q.getPrice())
                    .append(" days=").append(q.getPromisedDays())
                    .append(" yield=").append(q.getYieldRate())
                    .append(" maxQty=").append(q.getMaxQty())
                    .append(" dailyCapacitySum=").append(cap)
                    .append(" ").append(factoryProfile(q.getTenantId()))
                    .append(" capability=").append(e == null ? "" : nvl(e.getCapabilityJson()))
                    .append('\n');
            for (Device d : devices) {
                sb.append("  device=").append(nvl(d.getName()))
                        .append(" model=").append(nvl(d.getModel()))
                        .append(" precision=").append(nvl(d.getPrecisionText()))
                        .append(" dailyCapacity=").append(d.getDailyCapacity() == null ? "未填" : d.getDailyCapacity())
                        .append('\n');
            }
        }
        sb.append("\n输出格式：{\"schemes\":[{\"type\":\"AI1\",\"rationale\":\"整套方案总述\",\"risks\":[\"风险提示\"],");
        sb.append("\"items\":[{\"processNo\":1,\"factoryId\":15,\"reason\":\"选这家的具体理由\",");
        sb.append("\"capacityCheck\":\"该厂勾选设备日产能合计300件/天，需求需200件/天，余量50%\"}]}]}\n");
        sb.append("type 只能是 AI1/AI2，每套覆盖全部工序，factoryId 必须来自候选。每道工序必须写 reason 和 capacityCheck。价格用锁定报价，不要自编。");
        return sb.toString();
    }

    private String factoryProfile(Long tenantId) {
        if (tenantId == null) {
            return "";
        }
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<CreditEvent> events = creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId)
                .ge(CreditEvent::getCreatedAt, since)
                .orderByDesc(CreditEvent::getId)
                .last("limit 8"));
        String eventSummary = events.isEmpty() ? "近90天无信用事件"
                : ("近90天信用事件" + events.size() + "条：" + events.stream()
                .map(ev -> ev.getType() + (ev.getRemark() == null ? "" : "/" + ev.getRemark()))
                .reduce((a, b) -> a + "；" + b).orElse(""));
        List<CreditEvent> allEvents = creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId));
        long noLock = allEvents.stream().filter(ev -> "INTENTION_NO_LOCK".equals(ev.getType())).count();
        long cancel = allEvents.stream().filter(ev -> ev.getType() != null
                && (ev.getType().contains("CANCEL") || ev.getType().contains("取消"))).count();
        long violate = allEvents.stream().filter(ev -> ev.getType() != null
                && (ev.getType().contains("VIOLAT") || ev.getType().contains("PENALTY")
                || ev.getType().contains("FORFEIT") || ev.getType().contains("违规"))).count();
        String lifetime = "累计未锁价" + noLock + "次 取消/违约" + cancel + "次 违规/罚没" + violate + "次";
        Long settled = workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, tenantId)
                .eq(WorkStage::getEscrowStatus, "SETTLED"));
        List<WorkStage> factoryStages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, tenantId));
        List<Long> stageIds = factoryStages.stream().map(WorkStage::getId).toList();
        String passRate = "暂无质检记录";
        if (!stageIds.isEmpty()) {
            List<Inspection> ins = inspectionMapper.selectList(new LambdaQueryWrapper<Inspection>()
                    .in(Inspection::getStageId, stageIds));
            if (!ins.isEmpty()) {
                long pass = ins.stream().filter(i -> "PASS".equals(i.getResult())).count();
                passRate = "质检合格率" + String.format("%.0f%%", pass * 100.0 / ins.size()) + "（" + pass + "/" + ins.size() + "）";
            }
        }
        String surveyAvg = "暂无评价";
        if (!stageIds.isEmpty()) {
            List<Survey> surveys = surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                    .eq(Survey::getRole, "BUYER")
                    .in(Survey::getStageId, stageIds));
            List<Double> scores = new ArrayList<>();
            for (Survey s : surveys) {
                Double avg = avgSurvey(s.getScoresJson());
                if (avg != null) {
                    scores.add(avg);
                }
            }
            if (!scores.isEmpty()) {
                double mean = scores.stream().mapToDouble(x -> x).average().orElse(0);
                surveyAvg = "买家问卷均分" + String.format("%.1f", mean);
            }
        }
        return "完工工单" + (settled == null ? 0 : settled) + " " + passRate + " " + surveyAvg
                + " " + lifetime + " " + eventSummary;
    }

    private Double avgSurvey(String scoresJson) {
        if (scoresJson == null || scoresJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(scoresJson);
            double sum = 0;
            int c = 0;
            for (String k : List.of("q1", "q2", "q3", "q4")) {
                if (n.has(k)) {
                    sum += n.path(k).asDouble();
                    c++;
                }
            }
            return c == 0 ? null : sum / c;
        } catch (Exception e) {
            return null;
        }
    }

    private String callModel(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你只输出合法 JSON，不要 markdown。"),
                    Map.of("role", "user", "content", prompt)
            ));
            body.put("response_format", Map.of("type", "json_object"));
            String json = objectMapper.writeValueAsString(body);
            String url = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(75))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("AI 调用失败 http={} body={}", resp.statusCode(), resp.body());
                if (resp.statusCode() == 402) {
                    throw new BizException("AI 账户余额不足（402），规则方案仍可用");
                }
                throw new BizException("AI 服务返回 " + resp.statusCode());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BizException("AI 没有返回内容");
            }
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 调用异常", e);
            throw new BizException("AI 调用失败：" + e.getMessage());
        }
    }

    private List<SolutionCombo> parseAndHydrate(String content, List<Process> processes, List<Quotation> locked) {
        JsonNode root;
        try {
            String json = extractJson(content);
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            return List.of();
        }
        JsonNode schemes = root.has("schemes") ? root.get("schemes") : root;
        if (!schemes.isArray()) {
            return List.of();
        }
        List<SolutionCombo> result = new ArrayList<>();
        int i = 1;
        for (JsonNode scheme : schemes) {
            if (result.size() >= 3) {
                break;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            boolean ok = true;
            JsonNode arr = scheme.path("items");
            if (!arr.isArray()) {
                continue;
            }
            for (Process p : processes) {
                JsonNode chosen = null;
                for (JsonNode it : arr) {
                    if (p.getProcessNo() != null && p.getProcessNo() == it.path("processNo").asInt()) {
                        chosen = it;
                        break;
                    }
                }
                if (chosen == null) {
                    ok = false;
                    break;
                }
                Long factoryId = chosen.path("factoryId").asLong();
                int need = p.getQuantity() == null ? 0 : p.getQuantity();
                Quotation q = findLocked(locked, factoryId, p.getProcessNo(), need);
                if (q == null) {
                    ok = false;
                    break;
                }
                items.add(hydrate(p, q, chosen.path("reason").asText(""),
                        chosen.path("capacityCheck").asText("")));
            }
            if (!ok || items.size() != processes.size()) {
                continue;
            }
            String type = scheme.path("type").asText("AI" + i);
            if (!type.startsWith("AI")) {
                type = "AI" + i;
            }
            result.add(new SolutionCombo(type, scoreOf(items), items, packRationale(scheme)));
            i++;
        }
        return result;
    }

    private Map<String, Object> hydrate(Process p, Quotation q, String reason, String capacityCheck) {
        Enterprise e = enterpriseMapper.selectById(q.getTenantId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("processNo", p.getProcessNo());
        item.put("processName", p.getProcessName() == null ? "" : p.getProcessName());
        item.put("factoryId", q.getTenantId());
        item.put("factoryName", e == null ? ("厂" + q.getTenantId()) : e.getName());
        item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
        item.put("price", q.getPrice() == null ? BigDecimal.ZERO : q.getPrice());
        item.put("yieldRate", q.getYieldRate() == null ? BigDecimal.ZERO : q.getYieldRate());
        item.put("days", q.getPromisedDays() == null ? 0 : q.getPromisedDays());
        item.put("quantity", p.getQuantity() == null ? 0 : p.getQuantity());
        item.put("maxQty", q.getMaxQty());
        if (reason != null && !reason.isBlank()) {
            item.put("reason", reason);
        }
        if (capacityCheck != null && !capacityCheck.isBlank()) {
            item.put("capacityCheck", capacityCheck);
        }
        return item;
    }

    private String packRationale(JsonNode scheme) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rationale", scheme.path("rationale").asText(""));
            List<String> risks = new ArrayList<>();
            if (scheme.path("risks").isArray()) {
                scheme.path("risks").forEach(n -> {
                    if (n != null && !n.asText("").isBlank()) {
                        risks.add(n.asText());
                    }
                });
            }
            m.put("risks", risks);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return scheme.path("rationale").asText("");
        }
    }

    private Quotation findLocked(List<Quotation> locked, Long factoryId, Integer processNo, int need) {
        return locked.stream()
                .filter(q -> factoryId != null && factoryId.equals(q.getTenantId()))
                .filter(q -> processNo != null && processNo.equals(q.getProcessNo() == null ? 1 : q.getProcessNo()))
                .filter(q -> q.getMaxQty() == null || q.getMaxQty() >= need)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal scoreOf(List<Map<String, Object>> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal yield = item.get("yieldRate") instanceof BigDecimal y ? y : BigDecimal.ZERO;
            total = total.add(yield);
        }
        return items.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(items.size()), 4, java.math.RoundingMode.HALF_UP);
    }

    private String extractJson(String content) {
        String t = content.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('{');
            int end = t.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return t.substring(start, end + 1);
            }
        }
        return t;
    }

    private static String nvl(Object v) {
        return v == null ? "" : v.toString();
    }
}
