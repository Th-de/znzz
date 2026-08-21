package com.dsh.platform.domain.coverage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 按工序统计报名产能。意向报名、末日提醒、思考期取消、方案过滤共用这一份。
 */
@Service
@RequiredArgsConstructor
public class ProcessCoverageService {

    private final DemandMapper demandMapper;
    private final ProcessMapper processMapper;
    private final QuotationMapper quotationMapper;

    public CoverageView of(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        List<Process> processes = processMapper.selectList(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, demandId)
                .orderByAsc(Process::getProcessNo));
        if (processes.isEmpty()) {
            Process one = new Process();
            one.setProcessNo(1);
            one.setProcessName("整单");
            one.setQuantity(d.getQuantity());
            processes = List.of(one);
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        qs = qs.stream()
                .filter(q -> "LOCKED".equals(q.getStatus()) || "FROZEN".equals(q.getIntentionStatus()))
                .toList();
        Map<Integer, Integer> covered = qs.stream().collect(Collectors.groupingBy(
                q -> q.getProcessNo() == null ? 1 : q.getProcessNo(),
                Collectors.summingInt(q -> q.getMaxQty() == null ? 0 : q.getMaxQty())));

        List<ProcessCoverage> items = new ArrayList<>();
        boolean all = true;
        for (Process p : processes) {
            int need = p.getQuantity() == null ? 0 : p.getQuantity();
            int got = covered.getOrDefault(p.getProcessNo(), 0);
            boolean ok = need > 0 && got >= need;
            if (!ok) {
                all = false;
            }
            items.add(new ProcessCoverage(p.getProcessNo(), p.getProcessName(), need, got, ok));
        }
        return new CoverageView(demandId, d.getIntentionEndAt(), remainDays(d.getIntentionEndAt()), items, all);
    }

    public String unsatisfiedText(CoverageView view) {
        return view.processes().stream()
                .filter(p -> !p.satisfied())
                .map(p -> p.processName() + " 已覆盖 " + p.covered() + " / 需要 " + p.need())
                .collect(Collectors.joining("；"));
    }

    private long remainDays(LocalDateTime end) {
        if (end == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), end);
        return Math.max(days, 0);
    }
}
