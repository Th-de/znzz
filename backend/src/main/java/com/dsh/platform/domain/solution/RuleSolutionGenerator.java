package com.dsh.platform.domain.solution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则出 A/B/C。权重只有成本/工期/质量三项，下标必须对齐。
 */
@Component
@RequiredArgsConstructor
public class RuleSolutionGenerator implements SolutionGenerator {

    private final ProcessMapper processMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> alternatives(Demand demand, Integer processNo, int need) {
        List<Quotation> candidates = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getDemandId, demand.getId())
                        .eq(Quotation::getStatus, "LOCKED")
                        .eq(Quotation::getProcessNo, processNo))
                .stream()
                .filter(q -> q.getMaxQty() != null && q.getMaxQty() >= need)
                .toList();
        double[] w = demandWeights(demand);
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Quotation q) -> score(q, candidates, w)).reversed())
                .map(q -> {
                    Enterprise e = enterpriseMapper.selectById(q.getTenantId());
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("factoryId", q.getTenantId());
                    row.put("factoryName", e == null ? ("厂" + q.getTenantId()) : e.getName());
                    row.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
                    row.put("authStatus", e == null ? "" : e.getAuthStatus());
                    row.put("maxQty", q.getMaxQty());
                    row.put("price", q.getPrice());
                    row.put("yieldRate", q.getYieldRate());
                    row.put("days", q.getPromisedDays());
                    row.put("score", BigDecimal.valueOf(score(q, candidates, w)).setScale(4, RoundingMode.HALF_UP));
                    return row;
                })
                .toList();
    }

    @Override
    public List<SolutionCombo> generate(Demand demand) {
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
        Map<Integer, List<Quotation>> byProcess = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getDemandId, demand.getId())
                        .eq(Quotation::getStatus, "LOCKED"))
                .stream()
                .collect(Collectors.groupingBy(q -> q.getProcessNo() == null ? 1 : q.getProcessNo()));

        double[][] weights = {
                {0.70, 0.15, 0.15},
                demandWeights(demand),
                {0.15, 0.45, 0.40}
        };
        String[] types = {"A", "B", "C"};
        List<SolutionCombo> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Map<String, Object>> items = new ArrayList<>();
            double totalScore = 0;
            boolean complete = true;
            for (Process p : processes) {
                int need = p.getQuantity() == null ? 0 : p.getQuantity();
                List<Quotation> candidates = byProcess.getOrDefault(p.getProcessNo(), List.of()).stream()
                        .filter(q -> q.getMaxQty() != null && q.getMaxQty() >= need)
                        .toList();
                if (candidates.isEmpty()) {
                    complete = false;
                    break;
                }
                Quotation best = pickBest(candidates, weights[i]);
                totalScore += score(best, candidates, weights[i]);
                items.add(Map.of(
                        "processNo", p.getProcessNo(),
                        "processName", p.getProcessName() == null ? "" : p.getProcessName(),
                        "factoryId", best.getTenantId(),
                        "factoryName", factoryName(best.getTenantId()),
                        "creditScore", creditScore(best.getTenantId()),
                        "price", best.getPrice() == null ? BigDecimal.ZERO : best.getPrice(),
                        "yieldRate", best.getYieldRate() == null ? BigDecimal.ZERO : best.getYieldRate(),
                        "days", best.getPromisedDays() == null ? 0 : best.getPromisedDays(),
                        "quantity", need
                ));
            }
            if (!complete) {
                return List.of();
            }
            result.add(new SolutionCombo(types[i],
                    BigDecimal.valueOf(totalScore).setScale(4, RoundingMode.HALF_UP), items));
        }
        return result;
    }

    private double[] demandWeights(Demand d) {
        try {
            if (d.getWeightJson() == null || d.getWeightJson().isBlank()) {
                return new double[]{0.34, 0.33, 0.33};
            }
            JsonNode n = objectMapper.readTree(d.getWeightJson());
            return new double[]{
                    n.path("cost").asDouble(0.34),
                    n.path("time").asDouble(0.33),
                    n.path("quality").asDouble(0.33)
            };
        } catch (Exception e) {
            return new double[]{0.34, 0.33, 0.33};
        }
    }

    private Quotation pickBest(List<Quotation> candidates, double[] w) {
        return candidates.stream()
                .max(Comparator.comparingDouble(q -> score(q, candidates, w)))
                .orElse(candidates.get(0));
    }

    private double score(Quotation q, List<Quotation> pool, double[] w) {
        double price = q.getPrice() == null ? 0 : q.getPrice().doubleValue();
        double days = q.getPromisedDays() == null ? 0 : q.getPromisedDays();
        double yield = q.getYieldRate() == null ? 0 : q.getYieldRate().doubleValue();
        return w[0] * lowBetter(price, pool.stream().mapToDouble(x -> x.getPrice() == null ? 0 : x.getPrice().doubleValue()).toArray())
                + w[1] * lowBetter(days, pool.stream().mapToDouble(x -> x.getPromisedDays() == null ? 0 : x.getPromisedDays()).toArray())
                + w[2] * yield;
    }

    private double lowBetter(double value, double[] all) {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double v : all) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (max <= min) {
            return 1;
        }
        return 1 - (value - min) / (max - min);
    }

    private String factoryName(Long tenantId) {
        Enterprise e = enterpriseMapper.selectById(tenantId);
        return e == null ? ("厂" + tenantId) : e.getName();
    }

    private int creditScore(Long tenantId) {
        Enterprise e = enterpriseMapper.selectById(tenantId);
        return e == null || e.getCreditScore() == null ? 60 : e.getCreditScore();
    }
}
