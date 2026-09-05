package com.dsh.platform.domain.solution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.credit.SurveyScores;
import com.dsh.platform.domain.inspect.InspectPassRate;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
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
    private final InspectPassRate inspectPassRate;
    private final SurveyMapper surveyMapper;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    @Value("${dsh.ai.api-key:${DSH_AI_API_KEY:}}")
    private String apiKey;
    @Value("${dsh.ai.model:deepseek-chat}")
    private String model;
    @Value("${dsh.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public boolean configured() {
        return resolveKey() != null && !resolveKey().isBlank();
    }

    private String resolveKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String env = System.getenv("DSH_AI_API_KEY");
        return env == null ? "" : env.trim();
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
        List<SolutionCombo> parsed = parseAndHydrate(content, demand, processes, locked);
        if (parsed.isEmpty()) {
            throw new BizException("AI 方案无法落到已锁定报价，已放弃");
        }
        return parsed;
    }

    private String buildPrompt(Demand demand, List<Process> processes, List<Quotation> locked) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是制造业订单评审专家。综合产能可行性、历史履约记录、信用状况、质量表现、价格与工期，");
        sb.append("按零件件数把整单需求分给工厂，产出完整可执行的履约方案。\n");
        sb.append("一单一品：分给某厂的件数由该厂完成全部工序，不要按工序拆分件数，也不要按工序做小计。\n");
        sb.append("只能从候选工厂里选，不能发明工厂、价格或承接量。只返回 JSON。\n");
        sb.append("可把总需求件数拆给多家工厂：各厂 quantity 之和必须等于需求数量，");
        sb.append("每家分配量不得低于该厂约定的最小承接量 minQty，也不得超过该厂最大承接量 maxQty。");
        sb.append("若某厂 minQty 大于剩余可分配量，则不要选该厂。单价是该品一件的价格（含全部工序）。\n");
        sb.append("产能核算：需要的日产出 ≈ 分配数量 ÷ 承诺工期（天）。对比该厂勾选设备的日产能合计，判断是否可行、余量多少。\n");
        sb.append("生成 1~3 套最优推荐方案（至少 1 套，视候选丰富程度而定），每套给出总费用与分阶段交付节点, 如果只有一个阶段，则不要分阶段写明。\n\n");
        if (demand.getDeliveryTimes() != null && demand.getDeliveryTimes() > 0) {
            sb.append("买家要求分 ").append(demand.getDeliveryTimes()).append(" 期交付");
            if (demand.getDeliveryPlanJson() != null && !demand.getDeliveryPlanJson().isBlank()) {
                sb.append("，每期要求：").append(demand.getDeliveryPlanJson());
            }
            sb.append('\n');
        }
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
                    .append(" unitPrice=").append(q.getUnitPrice())
                    .append(" totalPrice=").append(q.getPrice())
                    .append(" days=").append(q.getPromisedDays())
                    .append(" yield=").append(q.getYieldRate())
                    .append(" minQty=").append(q.getMinQty())
                    .append(" maxQty=").append(q.getMaxQty())
                    .append(" dailyCapacitySum=").append(cap)
                    .append(" ").append(factoryProfile(q.getTenantId()))
                    .append(" capability=").append(e == null ? "" : nvl(e.getCapabilityJson()))
                    .append('\n');
            if (q.getPlanText() != null && !q.getPlanText().isBlank()) {
                sb.append("  实施方案：").append(q.getPlanText()).append('\n');
            }
            if (q.getDeliveryPlanJson() != null && !q.getDeliveryPlanJson().isBlank()) {
                sb.append("  分期交付承诺：").append(q.getDeliveryPlanJson()).append('\n');
            }
            for (Device d : devices) {
                sb.append("  device=").append(nvl(d.getName()))
                        .append(" model=").append(nvl(d.getModel()))
                        .append(" precision=").append(nvl(d.getPrecisionText()))
                        .append(" dailyCapacity=").append(d.getDailyCapacity() == null ? "未填" : d.getDailyCapacity())
                        .append('\n');
            }
        }
        sb.append("\n输出格式：{\"schemes\":[{\"type\":\"AI1\",\"rationale\":\"整套方案总述\",\"risks\":[\"风险提示\"],");
        sb.append("\"milestones\":[\"第1期：完成粗加工600件并交检\",\"第2期：…\"],");
        sb.append("\"items\":[{\"factoryId\":15,\"quantity\":600,\"reason\":\"选这家承接600件的具体理由\",");
        sb.append("\"capacityCheck\":\"该厂勾选设备日产能合计300件/天，分配600件需2天+，工期10天余量充足\"}]}]}\n");
        sb.append("type 依次为 AI1/AI2/AI3/AI4/AI5。每套 items 按工厂写一行，quantity 为该厂承接零件数，合计必须等于需求数量；");
        sb.append("不要按工序拆行。factoryId 必须来自候选，且 minQty<=quantity<=maxQty（minQty 为空时按 1）。每个 item 必须写 quantity、reason 和 capacityCheck。");
        sb.append("单价用候选 unitPrice，不要自编。milestones 按买家分期要求给出每阶段任务与交付节点。");
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
        long cancel = allEvents.stream().filter(ev -> ev.getType() != null
                && (ev.getType().contains("CANCEL") || ev.getType().contains("取消"))).count();
        long violate = allEvents.stream().filter(ev -> ev.getType() != null
                && (ev.getType().contains("VIOLAT") || ev.getType().contains("PENALTY")
                || ev.getType().contains("FORFEIT") || ev.getType().contains("违规")
                || ev.getType().startsWith("HONESTY_"))).count();
        String lifetime = "累计取消/违约" + cancel + "次 失信事件" + violate + "次";
        Long settled = workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, tenantId)
                .eq(WorkStage::getEscrowStatus, "SETTLED"));
        List<WorkStage> factoryStages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, tenantId));
        List<Long> stageIds = factoryStages.stream().map(WorkStage::getId).toList();
        InspectPassRate.Stats inspectStats = inspectPassRate.ofSettledStages(factoryStages);
        String passRate = inspectStats.percent() == null
                ? "暂无质检记录"
                : ("质检合格率" + String.format("%.1f%%", inspectStats.percent())
                + "（" + inspectStats.pass() + "/" + inspectStats.judged() + "）");
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
        return SurveyScores.overallStars(scoresJson, objectMapper);
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
                    .header("Authorization", "Bearer " + resolveKey())
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

    private List<SolutionCombo> parseAndHydrate(String content, Demand demand, List<Process> processes, List<Quotation> locked) {
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
        int demandQty = demand.getQuantity() == null ? 0 : demand.getQuantity();
        List<SolutionCombo> result = new ArrayList<>();
        int i = 1;
        for (JsonNode scheme : schemes) {
            if (result.size() >= 5) {
                break;
            }
            JsonNode arr = scheme.path("items");
            if (!arr.isArray()) {
                continue;
            }
            List<Map<String, Object>> items = hydrateByFactory(arr, demandQty, processes, locked);
            if (items.isEmpty()) {
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

    private List<Map<String, Object>> hydrateByFactory(JsonNode arr, int demandQty,
            List<Process> processes, List<Quotation> locked) {
        Map<Long, Integer> qtyByFactory = new LinkedHashMap<>();
        Map<Long, String[]> meta = new LinkedHashMap<>();
        for (JsonNode it : arr) {
            long factoryId = it.path("factoryId").asLong(0);
            if (factoryId <= 0) {
                continue;
            }
            int qty = it.path("quantity").asInt(0);
            if (qty <= 0) {
                qty = demandQty;
            }
            Integer held = qtyByFactory.get(factoryId);
            if (held == null) {
                qtyByFactory.put(factoryId, qty);
                meta.put(factoryId, new String[]{it.path("reason").asText(""), it.path("capacityCheck").asText("")});
            } else if (qty > held) {
                qtyByFactory.put(factoryId, qty);
            }
        }
        int sum = qtyByFactory.values().stream().mapToInt(Integer::intValue).sum();
        if (qtyByFactory.isEmpty() || sum != demandQty) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyByFactory.entrySet()) {
            Long factoryId = e.getKey();
            int qty = e.getValue();
            String[] m = meta.get(factoryId);
            for (Process p : processes) {
                Quotation q = findLocked(locked, factoryId, p.getProcessNo(), qty);
                if (q == null) {
                    return List.of();
                }
                items.add(hydrate(p, q, qty, m[0], m[1]));
            }
        }
        return items;
    }

    private Map<String, Object> hydrate(Process p, Quotation q, int quantity, String reason, String capacityCheck) {
        Enterprise e = enterpriseMapper.selectById(q.getTenantId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("processNo", p.getProcessNo());
        item.put("processName", p.getProcessName() == null ? "" : p.getProcessName());
        item.put("factoryId", q.getTenantId());
        item.put("factoryName", e == null ? ("厂" + q.getTenantId()) : e.getName());
        item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
        item.put("unitPrice", unitPriceOf(q));
        item.put("price", priceFor(q, quantity));
        item.put("yieldRate", q.getYieldRate() == null ? BigDecimal.ZERO : q.getYieldRate());
        item.put("days", q.getPromisedDays() == null ? 0 : q.getPromisedDays());
        item.put("quantity", quantity);
        item.put("minQty", q.getMinQty());
        item.put("maxQty", q.getMaxQty());
        if (reason != null && !reason.isBlank()) {
            item.put("reason", reason);
        }
        if (capacityCheck != null && !capacityCheck.isBlank()) {
            item.put("capacityCheck", capacityCheck);
        }
        return item;
    }

    /** 分配量的费用 = 单价 × 分配量；无单价时按总价折算。 */
    private BigDecimal priceFor(Quotation q, int quantity) {
        BigDecimal unit = unitPriceOf(q);
        if (unit.compareTo(BigDecimal.ZERO) > 0) {
            return unit.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return q.getPrice() == null ? BigDecimal.ZERO : q.getPrice();
    }

    private BigDecimal unitPriceOf(Quotation q) {
        if (q.getUnitPrice() != null && q.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return q.getUnitPrice();
        }
        if (q.getPrice() != null && q.getMaxQty() != null && q.getMaxQty() > 0) {
            return q.getPrice().divide(BigDecimal.valueOf(q.getMaxQty()), 2, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
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
            List<String> milestones = new ArrayList<>();
            if (scheme.path("milestones").isArray()) {
                scheme.path("milestones").forEach(n -> {
                    if (n != null && !n.asText("").isBlank()) {
                        milestones.add(n.asText());
                    }
                });
            }
            m.put("milestones", milestones);
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
                .filter(q -> q.getMinQty() == null || need >= q.getMinQty())
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
