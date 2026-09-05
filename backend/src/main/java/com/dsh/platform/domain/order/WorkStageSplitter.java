package com.dsh.platform.domain.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按「工厂 × 交付期」拆工单。该厂承接件数走完全部工序，金额为单价×件数（不按工序累加），按期分摊。
 */
@Component
@RequiredArgsConstructor
public class WorkStageSplitter {

    private final WorkStageMapper workStageMapper;
    private final DemandMapper demandMapper;
    private final ObjectMapper objectMapper;

    public void splitForFactory(Order order, Solution solution, Long factoryId) {
        if (order == null || solution == null || factoryId == null) {
            throw new BizException("订单、方案或工厂不存在");
        }
        Long exists = workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, order.getId())
                .eq(WorkStage::getTenantId, factoryId));
        if (exists != null && exists > 0) {
            throw new BizException("该厂工单已拆分，不能重复拆");
        }
        List<Map<String, Object>> mine = readCombo(solution.getFinalComboJson()).stream()
                .filter(item -> factoryId.equals(asLong(item.get("factoryId"))))
                .toList();
        if (mine.isEmpty()) {
            throw new BizException("方案中没有该厂的工序");
        }
        int quantity = 0;
        BigDecimal unitPrice = null;
        BigDecimal linePrice = null;
        int days = 1;
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> item : mine) {
            Integer q = asInt(item.get("quantity"));
            if (q != null && q > quantity) {
                quantity = q;
            }
            BigDecimal u = asDecimal(item.get("unitPrice"));
            if (unitPrice == null && u != null && u.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = u;
            }
            BigDecimal p = asDecimal(item.get("price"));
            if (linePrice == null && p != null && p.compareTo(BigDecimal.ZERO) > 0) {
                linePrice = p;
            }
            Integer d = asInt(item.get("days"));
            if (d != null && d > days) {
                days = d;
            }
            if (item.get("processName") != null && !item.get("processName").toString().isBlank()) {
                names.add(item.get("processName").toString().trim());
            }
        }
        BigDecimal price = unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0 && quantity > 0
                ? unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
                : (linePrice == null ? BigDecimal.ZERO : linePrice);
        String processName = names.isEmpty() ? "全部工序" : String.join("+", names);
        Demand demand = demandMapper.selectById(order.getDemandId());
        List<PeriodSpec> periods = periodsOf(demand);
        BigDecimal usedAmt = BigDecimal.ZERO;
        int usedQty = 0;
        int planWeight = periods.stream().mapToInt(p -> Math.max(0, p.percent)).sum();
        boolean usePlan = planWeight > 0;
        for (int i = 0; i < periods.size(); i++) {
            boolean last = i == periods.size() - 1;
            PeriodSpec p = periods.get(i);
            int segQty;
            if (last) {
                segQty = Math.max(0, quantity - usedQty);
            } else if (usePlan) {
                segQty = quantity * p.percent / planWeight;
            } else {
                segQty = quantity / periods.size();
            }
            usedQty += segQty;
            BigDecimal amt = last || quantity <= 0 ? price.subtract(usedAmt)
                    : price.multiply(BigDecimal.valueOf(segQty))
                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            usedAmt = usedAmt.add(amt);
            String label = "第" + p.no + "期" + (p.percent > 0 ? "：" + p.percent + "%"
                    : (p.text.isBlank() ? "" : "：" + p.text));
            insertStage(order, factoryId, 1, label, segQty, days, amt, p);
        }
    }

    private List<PeriodSpec> periodsOf(Demand demand) {
        int times = demand == null || demand.getDeliveryTimes() == null ? 1 : Math.max(1, demand.getDeliveryTimes());
        List<JsonNode> plan = readPlanNodes(demand == null ? null : demand.getDeliveryPlanJson());
        LocalDate hard = demand == null ? null : demand.getDeadlineHard();
        List<PeriodSpec> out = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            JsonNode n = i < plan.size() ? plan.get(i) : null;
            String text = textOf(n);
            int percent = percentOf(n);
            LocalDateTime start = startOf(n, i, times, hard);
            LocalDateTime end = endOf(n, i, times, hard, start);
            out.add(new PeriodSpec(i + 1, text, percent, start, end));
        }
        return out;
    }

    private int percentOf(JsonNode n) {
        if (n == null || n.isNull()) {
            return 0;
        }
        if (n.has("percent") && n.get("percent").canConvertToInt()) {
            return Math.max(0, n.get("percent").asInt());
        }
        if (n.has("qty") && n.get("qty").canConvertToInt()) {
            int q = n.get("qty").asInt();
            if (q >= 1 && q <= 100) {
                return q;
            }
        }
        String raw = n.isTextual() ? n.asText("") : n.path("text").asText("");
        if (raw != null && raw.contains("%")) {
            String digits = raw.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    return Math.max(0, Integer.parseInt(digits));
                } catch (Exception ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private String textOf(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }
        if (n.isTextual()) {
            return n.asText("");
        }
        return n.path("text").asText("");
    }

    private LocalDateTime startOf(JsonNode n, int index, int times, LocalDate hard) {
        LocalDate d = dateOf(n, "startAt");
        if (d != null) {
            return LocalDateTime.of(d, LocalTime.MIN);
        }
        if (hard != null && times > 1) {
            long span = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), hard));
            long step = Math.max(1, span / times);
            return LocalDateTime.of(LocalDate.now().plusDays(index * step), LocalTime.MIN);
        }
        return LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    }

    private LocalDateTime endOf(JsonNode n, int index, int times, LocalDate hard, LocalDateTime start) {
        LocalDate d = dateOf(n, "endAt");
        if (d != null) {
            return LocalDateTime.of(d, LocalTime.of(23, 59, 59));
        }
        if (hard != null) {
            if (index == times - 1) {
                return LocalDateTime.of(hard, LocalTime.of(23, 59, 59));
            }
            long span = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), hard));
            long step = Math.max(1, span / Math.max(1, times - index));
            return LocalDateTime.of(start.toLocalDate().plusDays(step).minusDays(1), LocalTime.of(23, 59, 59));
        }
        return start.plusDays(6).withHour(23).withMinute(59).withSecond(59);
    }

    private LocalDate dateOf(JsonNode n, String field) {
        if (n == null || n.isNull() || n.isTextual()) {
            return null;
        }
        String raw = n.path(field).asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
        } catch (Exception e) {
            return null;
        }
    }

    private List<JsonNode> readPlanNodes(String json) {
        List<JsonNode> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.isArray()) {
                n.forEach(list::add);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return list;
    }

    private void insertStage(Order order, Long factoryId, Integer processNo, String name,
                             Integer quantity, Integer days, BigDecimal amount, PeriodSpec period) {
        WorkStage ws = new WorkStage();
        ws.setOrderId(order.getId());
        ws.setTenantId(factoryId);
        ws.setProcessNo(processNo == null ? 1 : processNo);
        ws.setProcessName(name);
        ws.setQuantity(quantity);
        ws.setPromisedDays(days);
        ws.setPromisedDate(period.end.toLocalDate());
        ws.setAmount(amount);
        ws.setEscrowStatus("NONE");
        ws.setActualProgress(0);
        ws.setPeriodNo(period.no);
        ws.setPeriodStart(period.start);
        ws.setPeriodEnd(period.end);
        ws.setStatus("WAITING_OPEN");
        ws.setInspectFeeStatus("NONE");
        workStageMapper.insert(ws);
    }

    private List<Map<String, Object>> readCombo(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("方案数据解析失败");
        }
    }

    private static Long asLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private static Integer asInt(Object v) {
        return v == null ? null : Integer.valueOf(new BigDecimal(v.toString()).intValue());
    }

    private static BigDecimal asDecimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private record PeriodSpec(int no, String text, int percent, LocalDateTime start, LocalDateTime end) {}
}
