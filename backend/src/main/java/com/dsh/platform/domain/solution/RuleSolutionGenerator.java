package com.dsh.platform.domain.solution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则方案已暂停，主流程走 AI。本类仅保留换厂候选 alternatives()。
 * 原 generate / 三权重打分代码注释备查，不要删除。
 */
@Component
@RequiredArgsConstructor
public class RuleSolutionGenerator implements SolutionGenerator {

    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DeviceService deviceService;

    public List<Map<String, Object>> alternatives(Demand demand, Integer processNo, int need) {
        List<Quotation> candidates = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getDemandId, demand.getId())
                        .eq(Quotation::getStatus, "LOCKED")
                        .eq(Quotation::getProcessNo, processNo))
                .stream()
                .filter(q -> q.getMaxQty() != null && q.getMaxQty() >= need)
                .toList();
        return candidates.stream()
                .map(q -> {
                    Enterprise e = enterpriseMapper.selectById(q.getTenantId());
                    List<Device> devices = deviceService.byIds(deviceService.parseIds(q.getDeviceIdsJson()));
                    int cap = deviceService.dailyCapacitySum(devices);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("factoryId", q.getTenantId());
                    row.put("factoryName", e == null ? ("厂" + q.getTenantId()) : e.getName());
                    row.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
                    row.put("authStatus", e == null ? "" : e.getAuthStatus());
                    row.put("maxQty", q.getMaxQty());
                    row.put("price", q.getPrice());
                    row.put("yieldRate", q.getYieldRate());
                    row.put("days", q.getPromisedDays());
                    row.put("dailyCapacity", cap);
                    row.put("devices", devices.stream()
                            .map(d -> d.getName() + (d.getDailyCapacity() == null ? "" : (" " + d.getDailyCapacity() + "件/天")))
                            .toList());
                    return row;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> {
                    Object c = m.get("creditScore");
                    return c instanceof Number n ? n.intValue() : 0;
                }).reversed())
                .toList();
    }

    /** 规则方案暂停，主流程不再调用。接口保留以免 Spring 装配失败。 */
    @Override
    public List<SolutionCombo> generate(Demand demand) {
        return List.of();
    }

    /*
    // ===== 以下为原规则 A/B/C 三权重方案，暂停使用，保留备查 =====
    //
    // public List<SolutionCombo> generate(Demand demand) {
    //     List<Process> processes = processMapper.selectList(new LambdaQueryWrapper<Process>()
    //             .eq(Process::getDemandId, demand.getId())
    //             .orderByAsc(Process::getProcessNo));
    //     if (processes.isEmpty()) {
    //         Process one = new Process();
    //         one.setProcessNo(1);
    //         one.setProcessName("整单");
    //         one.setQuantity(demand.getQuantity());
    //         processes = List.of(one);
    //     }
    //     Map<Integer, List<Quotation>> byProcess = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
    //                     .eq(Quotation::getDemandId, demand.getId())
    //                     .eq(Quotation::getStatus, "LOCKED"))
    //             .stream()
    //             .collect(Collectors.groupingBy(q -> q.getProcessNo() == null ? 1 : q.getProcessNo()));
    //     double[][] weights = {
    //             {0.70, 0.15, 0.15},
    //             demandWeights(demand),
    //             {0.15, 0.45, 0.40}
    //     };
    //     String[] types = {"A", "B", "C"};
    //     List<SolutionCombo> result = new ArrayList<>();
    //     for (int i = 0; i < 3; i++) {
    //         List<Map<String, Object>> items = new ArrayList<>();
    //         double totalScore = 0;
    //         boolean complete = true;
    //         for (Process p : processes) {
    //             int need = p.getQuantity() == null ? 0 : p.getQuantity();
    //             List<Quotation> candidates = byProcess.getOrDefault(p.getProcessNo(), List.of()).stream()
    //                     .filter(q -> q.getMaxQty() != null && q.getMaxQty() >= need)
    //                     .toList();
    //             if (candidates.isEmpty()) {
    //                 complete = false;
    //                 break;
    //             }
    //             Quotation best = pickBest(candidates, weights[i]);
    //             totalScore += score(best, candidates, weights[i]);
    //             items.add(Map.of(
    //                     "processNo", p.getProcessNo(),
    //                     "processName", p.getProcessName() == null ? "" : p.getProcessName(),
    //                     "factoryId", best.getTenantId(),
    //                     "factoryName", factoryName(best.getTenantId()),
    //                     "creditScore", creditScore(best.getTenantId()),
    //                     "price", best.getPrice() == null ? BigDecimal.ZERO : best.getPrice(),
    //                     "yieldRate", best.getYieldRate() == null ? BigDecimal.ZERO : best.getYieldRate(),
    //                     "days", best.getPromisedDays() == null ? 0 : best.getPromisedDays(),
    //                     "quantity", need
    //             ));
    //         }
    //         if (!complete) {
    //             return List.of();
    //         }
    //         result.add(new SolutionCombo(types[i],
    //                 BigDecimal.valueOf(totalScore).setScale(4, RoundingMode.HALF_UP), items));
    //     }
    //     return result;
    // }
    //
    // private double[] demandWeights(Demand d) { ... 读 demand.weightJson 成本/工期/质量 ... }
    // private Quotation pickBest(List<Quotation> candidates, double[] w) { ... }
    // private double score(Quotation q, List<Quotation> pool, double[] w) { ... }
    // private double lowBetter(double value, double[] all) { ... }
    */

}
