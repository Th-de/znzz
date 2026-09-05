package com.dsh.platform.domain.quote;

import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 买家思考期参考：按厂区间最高值加权的去极值均价与预估总金额。 */
public final class QuoteEstimate {

    private QuoteEstimate() {}

    public static Map<String, Object> of(Demand demand, List<Quotation> locked) {
        Map<Long, Row> byFactory = new LinkedHashMap<>();
        for (Quotation q : locked == null ? List.<Quotation>of() : locked) {
            if (q.getTenantId() == null) {
                continue;
            }
            BigDecimal unit = unitOf(q);
            int max = Math.max(q.getMaxQty() == null ? 0 : q.getMaxQty(),
                    q.getMinQty() == null ? 0 : q.getMinQty());
            Row cur = byFactory.get(q.getTenantId());
            if (cur == null) {
                byFactory.put(q.getTenantId(), new Row(q.getTenantId(), unit, max));
            } else {
                if (max > cur.maxQty) {
                    cur.maxQty = max;
                }
                if (cur.unit.compareTo(BigDecimal.ZERO) <= 0 && unit.compareTo(BigDecimal.ZERO) > 0) {
                    cur.unit = unit;
                }
            }
        }
        List<Row> rows = new ArrayList<>(byFactory.values());
        rows.removeIf(r -> r.unit.compareTo(BigDecimal.ZERO) <= 0 || r.maxQty <= 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("factoryCount", rows.size());
        if (rows.isEmpty()) {
            out.put("weightedUnit", BigDecimal.ZERO);
            out.put("estimatedTotal", BigDecimal.ZERO);
            return out;
        }
        List<Row> forAvg = rows;
        if (rows.size() >= 3) {
            List<Row> sorted = new ArrayList<>(rows);
            sorted.sort(Comparator.comparing(r -> r.unit));
            Long lowId = sorted.get(0).factoryId;
            Long highId = sorted.get(sorted.size() - 1).factoryId;
            forAvg = rows.stream().filter(r -> !r.factoryId.equals(lowId) && !r.factoryId.equals(highId)).toList();
            if (forAvg.isEmpty()) {
                forAvg = rows;
            }
        }
        int weightSum = forAvg.stream().mapToInt(r -> r.maxQty).sum();
        BigDecimal weighted = BigDecimal.ZERO;
        if (weightSum > 0) {
            BigDecimal acc = BigDecimal.ZERO;
            for (Row r : forAvg) {
                acc = acc.add(r.unit.multiply(BigDecimal.valueOf(r.maxQty)));
            }
            weighted = acc.divide(BigDecimal.valueOf(weightSum), 4, RoundingMode.HALF_UP);
        }
        int need = demand == null || demand.getQuantity() == null ? 0 : demand.getQuantity();
        BigDecimal total = weighted.multiply(BigDecimal.valueOf(need)).setScale(2, RoundingMode.HALF_UP);
        out.put("weightedUnit", weighted.setScale(4, RoundingMode.HALF_UP));
        out.put("estimatedTotal", total);
        return out;
    }

    private static BigDecimal unitOf(Quotation q) {
        if (q.getUnitPrice() != null && q.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return q.getUnitPrice();
        }
        if (q.getPrice() != null && q.getMaxQty() != null && q.getMaxQty() > 0) {
            return q.getPrice().divide(BigDecimal.valueOf(q.getMaxQty()), 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static final class Row {
        final Long factoryId;
        BigDecimal unit;
        int maxQty;

        Row(Long factoryId, BigDecimal unit, int maxQty) {
            this.factoryId = factoryId;
            this.unit = unit == null ? BigDecimal.ZERO : unit;
            this.maxQty = maxQty;
        }
    }
}
