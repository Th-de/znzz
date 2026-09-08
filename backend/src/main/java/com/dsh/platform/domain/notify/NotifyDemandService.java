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
        Map<Long, Boolean> factoryWon = new HashMap<>();
        Map<Long, Boolean> factoryLost = new HashMap<>();
        Map<Long, LocalDateTime> joinedAt = joinedTimes(tenantId, role, factoryWon, factoryLost);
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
            List<NotifyView> notifies = new ArrayList<>(byDemand.getOrDefault(e.getKey(), List.of()));
            NotifyDemandRow row = new NotifyDemandRow();
            row.setDemandId(d.getId());
            row.setTitle(d.getTitle());
            row.setStatus(d.getStatus());
            boolean lostOnly = "FACTORY".equals(role)
                    && Boolean.TRUE.equals(factoryLost.get(d.getId()))
                    && !Boolean.TRUE.equals(factoryWon.get(d.getId()));
            if (lostOnly) {
                row.setStatus("LOSE");
                ensureLoseNotify(notifies, d);
            }
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
    private Map<Long, LocalDateTime> joinedTimes(Long tenantId, String role,
                                                Map<Long, Boolean> factoryWon, Map<Long, Boolean> factoryLost) {
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
                if ("WIN".equals(q.getStatus())) {
                    factoryWon.put(q.getDemandId(), true);
                }
                if ("LOSE".equals(q.getStatus())) {
                    factoryLost.put(q.getDemandId(), true);
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

    /** 落选当时未写库的历史数据，列表里补一条落选通知。 */
    private void ensureLoseNotify(List<NotifyView> notifies, Demand d) {
        for (NotifyView v : notifies) {
            if (v.getTitle() != null && v.getTitle().startsWith("已落选#")) {
                return;
            }
        }
        NotifyView v = new NotifyView();
        v.setDemandId(d.getId());
        v.setTitle("已落选#" + d.getId());
        v.setContent("需求「" + d.getTitle() + "」方案已确定，本厂未入选，报名状态已变为已落选。后续不再推送该需求相关通知。");
        v.setIsRead(1);
        v.setLink("/factory/quotations/" + d.getId());
        v.setAction("查看报名");
        v.setCreatedAt(notifies.isEmpty() ? d.getUpdatedAt() : notifies.get(0).getCreatedAt());
        notifies.add(0, v);
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
