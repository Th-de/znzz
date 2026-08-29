package com.dsh.platform.domain.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 只拆该厂在方案里的工序。金额用该厂该工序锁定价；该厂自己分段时均分该工序价。
 */
@Component
@RequiredArgsConstructor
public class WorkStageSplitter {

    private final WorkStageMapper workStageMapper;
    private final QuotationMapper quotationMapper;
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
        for (Map<String, Object> item : mine) {
            insertForItem(order, item);
        }
    }

    private void insertForItem(Order order, Map<String, Object> item) {
        Long factoryId = asLong(item.get("factoryId"));
        Integer processNo = asInt(item.get("processNo"));
        String processName = item.get("processName") == null ? "工序" : item.get("processName").toString();
        Integer quantity = asInt(item.get("quantity"));
        if (quantity == null) {
            quantity = 0;
        }
        Integer days = asInt(item.get("days"));
        if (days == null || days <= 0) {
            days = 1;
        }
        BigDecimal price = asDecimal(item.get("price"));
        Quotation q = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, order.getDemandId())
                .eq(Quotation::getTenantId, factoryId)
                .eq(Quotation::getProcessNo, processNo)
                .in(Quotation::getStatus, "LOCKED", "WIN")
                .last("limit 1"));
        // 新流程：按买家规定的分期数拆段，段名带工厂填报的每期交付内容
        Demand demand = demandMapper.selectById(order.getDemandId());
        int periods = demand == null || demand.getDeliveryTimes() == null ? 0 : demand.getDeliveryTimes();
        if (periods > 1) {
            List<String> plan = readPlan(q == null ? null : q.getDeliveryPlanJson());
            BigDecimal usedAmt = BigDecimal.ZERO;
            int usedQty = 0;
            int usedDays = 0;
            for (int i = 0; i < periods; i++) {
                boolean last = i == periods - 1;
                BigDecimal amt = last ? price.subtract(usedAmt)
                        : price.divide(BigDecimal.valueOf(periods), 2, RoundingMode.DOWN);
                usedAmt = usedAmt.add(amt);
                int segQty = last ? Math.max(0, quantity - usedQty) : quantity / periods;
                usedQty += segQty;
                int segDays = last ? Math.max(1, days - usedDays) : Math.max(1, days / periods);
                usedDays += segDays;
                String content = i < plan.size() && plan.get(i) != null && !plan.get(i).isBlank()
                        ? "：" + plan.get(i) : "";
                insertStage(order, factoryId, processNo,
                        processName + "·第" + (i + 1) + "期" + content, segQty, segDays, amt);
            }
            return;
        }
        List<JsonNode> segs = readCurve(q == null ? null : q.getStageCurveJson());
        if (segs.isEmpty()) {
            insertStage(order, factoryId, processNo, processName, quantity, days, price);
            return;
        }
        int n = segs.size();
        BigDecimal used = BigDecimal.ZERO;
        int usedDays = 0;
        for (int i = 0; i < n; i++) {
            JsonNode seg = segs.get(i);
            boolean last = i == n - 1;
            BigDecimal amt = seg.hasNonNull("amount")
                    ? seg.get("amount").decimalValue()
                    : (last ? price.subtract(used) : price.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN));
            used = used.add(amt);
            int d = seg.hasNonNull("days")
                    ? seg.get("days").asInt(1)
                    : (last ? Math.max(1, days - usedDays) : Math.max(1, days / n));
            usedDays += d;
            String name = seg.path("name").asText("").isBlank()
                    ? processName + "·段" + (i + 1)
                    : processName + "·" + seg.path("name").asText();
            insertStage(order, factoryId, processNo, name, quantity, d, amt);
        }
    }

    private List<String> readPlan(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void insertStage(Order order, Long factoryId, Integer processNo, String name,
                             Integer quantity, Integer days, BigDecimal amount) {
        WorkStage ws = new WorkStage();
        ws.setOrderId(order.getId());
        ws.setTenantId(factoryId);
        ws.setProcessNo(processNo == null ? 1 : processNo);
        ws.setProcessName(name);
        ws.setQuantity(quantity);
        ws.setPromisedDays(days);
        ws.setPromisedDate(LocalDate.now().plusDays(days));
        ws.setAmount(amount);
        ws.setEscrowStatus("NONE");
        ws.setActualProgress(0);
        ws.setStatus("PENDING");
        workStageMapper.insert(ws);
    }

    private List<Map<String, Object>> readCombo(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("方案数据解析失败");
        }
    }

    private List<JsonNode> readCurve(String json) {
        List<JsonNode> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.isArray()) {
                n.forEach(list::add);
            } else if (n.has("stages") && n.get("stages").isArray()) {
                n.get("stages").forEach(list::add);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return list;
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
}
