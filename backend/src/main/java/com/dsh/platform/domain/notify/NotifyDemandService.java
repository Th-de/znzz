package com.dsh.platform.domain.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.dto.NotifyDemandRow;
import com.dsh.platform.dto.NotifyView;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.NotifyMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotifyDemandService {

    private final NotifyMapper notifyMapper;
    private final DemandMapper demandMapper;
    private final QuotationMapper quotationMapper;
    private final NotifyActionResolver notifyActionResolver;

    public List<NotifyDemandRow> mine() {
        Long tenantId = UserContext.tenantId();
        String role = UserContext.role();
        Map<Long, LocalDateTime> joinedAt = joinedTimes(tenantId, role);
        if (joinedAt.isEmpty()) {
            return List.of();
        }
        Map<Long, Demand> demands = loadDemands(joinedAt.keySet());
        Map<Long, List<NotifyView>> byDemand = new HashMap<>();
        List<Notify> list = notifyMapper.selectList(new LambdaQueryWrapper<Notify>()
                .eq(Notify::getTenantId, tenantId)
                .orderByDesc(Notify::getCreatedAt)
                .orderByDesc(Notify::getId));
        for (Notify n : list) {
            Long demandId = n.getDemandId();
            if (demandId == null) {
                demandId = notifyActionResolver.resolveDemandId(n.getTitle());
            }
            if (demandId == null || !joinedAt.containsKey(demandId)) {
                continue;
            }
            List<NotifyView> bucket = byDemand.computeIfAbsent(demandId, k -> new ArrayList<>());
            NotifyView view = notifyActionResolver.view(n, role, demands.get(demandId));
            if (duplicateNotify(bucket, view)) {
                continue;
            }
            bucket.add(view);
        }
        List<NotifyDemandRow> rows = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> e : joinedAt.entrySet()) {
            Demand d = demands.get(e.getKey());
            if (d == null) {
                continue;
            }
            List<NotifyView> notifies = byDemand.getOrDefault(e.getKey(), List.of());
            NotifyDemandRow row = new NotifyDemandRow();
            row.setDemandId(d.getId());
            row.setTitle(d.getTitle());
            row.setStatus(d.getStatus());
            row.setBidAt(e.getValue());
            if (!notifies.isEmpty()) {
                row.setLatestNotifyAt(notifies.get(0).getCreatedAt());
            }
            row.setNotifies(notifies);
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing(NotifyDemandRow::getBidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NotifyDemandRow::getDemandId, Comparator.reverseOrder()));
        return rows;
    }

    /** 买家：自己发布的需求；工厂：自己报过名的需求。 */
    private Map<Long, LocalDateTime> joinedTimes(Long tenantId, String role) {
        Map<Long, LocalDateTime> out = new LinkedHashMap<>();
        if ("FACTORY".equals(role)) {
            List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                    .eq(Quotation::getTenantId, tenantId)
                    .isNotNull(Quotation::getDemandId));
            for (Quotation q : qs) {
                LocalDateTime at = q.getCreatedAt();
                LocalDateTime old = out.get(q.getDemandId());
                if (old == null || (at != null && at.isBefore(old))) {
                    out.put(q.getDemandId(), at);
                }
            }
            return out;
        }
        if ("BUYER".equals(role)) {
            List<Demand> mine = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                    .eq(Demand::getTenantId, tenantId)
                    .ne(Demand::getStatus, "DRAFT"));
            for (Demand d : mine) {
                LocalDateTime at = d.getPublishedAt() != null ? d.getPublishedAt() : d.getCreatedAt();
                out.put(d.getId(), at);
            }
        }
        return out;
    }

    private static boolean duplicateNotify(List<NotifyView> bucket, NotifyView view) {
        String title = view.getTitle() == null ? "" : view.getTitle();
        String content = view.getContent() == null ? "" : view.getContent();
        for (NotifyView old : bucket) {
            if (title.equals(old.getTitle() == null ? "" : old.getTitle())
                    && content.equals(old.getContent() == null ? "" : old.getContent())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, Demand> loadDemands(Iterable<Long> ids) {
        List<Long> list = new ArrayList<>();
        ids.forEach(list::add);
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<Long, Demand> out = new HashMap<>();
        for (Demand d : demandMapper.selectBatchIds(list)) {
            out.put(d.getId(), d);
        }
        return out;
    }
}
