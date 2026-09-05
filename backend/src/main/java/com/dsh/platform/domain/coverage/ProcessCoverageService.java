package com.dsh.platform.domain.coverage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.QuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一单一品：每件都要走完全部工序，覆盖按整单零件件数统计（同一工厂多道工序报价只计一次承接量）。
 */
@Service
@RequiredArgsConstructor
public class ProcessCoverageService {

    private final DemandMapper demandMapper;
    private final QuotationMapper quotationMapper;

    public CoverageView of(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        qs = qs.stream()
                .filter(q -> "LOCKED".equals(q.getStatus())
                        || "FROZEN".equals(q.getIntentionStatus())
                        || "COVERED".equals(q.getIntentionStatus()))
                .toList();
        int need = d.getQuantity() == null ? 0 : d.getQuantity();
        int got = coveredPieces(qs);
        boolean ok = need > 0 && got >= need;
        List<ProcessCoverage> items = List.of(new ProcessCoverage(0, "零件覆盖", need, got, ok));
        return new CoverageView(demandId, d.getIntentionEndAt(), remainDays(d.getIntentionEndAt()), items, ok);
    }

    public boolean committedPiecesCovered(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            return false;
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getStatus, "LOCKED"));
        int need = d.getQuantity() == null ? 0 : d.getQuantity();
        return need > 0 && coveredPieces(qs) >= need;
    }

    public String unsatisfiedText(CoverageView view) {
        if (view.allSatisfied()) {
            return "零件报名产能已满足。";
        }
        return view.processes().stream()
                .filter(p -> !p.satisfied())
                .map(p -> "零件已覆盖 " + p.covered() + " / 需要 " + p.need())
                .collect(Collectors.joining("；"));
    }

    private static int coveredPieces(List<Quotation> qs) {
        Map<Long, Integer> byFactory = new HashMap<>();
        for (Quotation q : qs) {
            if (q.getTenantId() == null) {
                continue;
            }
            int cap = q.getMaxQty() == null ? 0 : q.getMaxQty();
            byFactory.merge(q.getTenantId(), cap, Math::max);
        }
        return byFactory.values().stream().mapToInt(Integer::intValue).sum();
    }

    private long remainDays(LocalDateTime end) {
        if (end == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), end);
        return Math.max(days, 0);
    }
}
