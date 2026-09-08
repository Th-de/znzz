package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.InspectionMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreditQueryService {

    private final CreditEventMapper creditEventMapper;
    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final WorkStageMapper workStageMapper;
    private final InspectionMapper inspectionMapper;

    public List<CreditEvent> mine(Long tenantId) {
        List<CreditEvent> list = creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, tenantId)
                .ne(CreditEvent::getType, "STAGE_PASS")
                .ne(CreditEvent::getScoreChange, 0)
                .orderByDesc(CreditEvent::getId));
        fillDemand(list);
        return list;
    }

    public void fillDemand(List<CreditEvent> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> demandIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        Set<Long> stageIds = new HashSet<>();
        Set<Long> inspectIds = new HashSet<>();
        for (CreditEvent ev : list) {
            if (ev.getRefId() == null || ev.getRefType() == null) {
                continue;
            }
            switch (ev.getRefType()) {
                case "DEMAND" -> demandIds.add(ev.getRefId());
                case "ORDER" -> orderIds.add(ev.getRefId());
                case "STAGE" -> stageIds.add(ev.getRefId());
                case "INSPECTION" -> inspectIds.add(ev.getRefId());
                default -> { }
            }
        }
        Map<Long, Inspection> inspections = byId(inspectionMapper, inspectIds, Inspection::getId);
        for (Inspection i : inspections.values()) {
            if (i.getStageId() != null) {
                stageIds.add(i.getStageId());
            }
        }
        Map<Long, WorkStage> stages = byId(workStageMapper, stageIds, WorkStage::getId);
        for (WorkStage ws : stages.values()) {
            if (ws.getOrderId() != null) {
                orderIds.add(ws.getOrderId());
            }
        }
        Map<Long, Order> orders = byId(orderMapper, orderIds, Order::getId);
        for (Order o : orders.values()) {
            if (o.getDemandId() != null) {
                demandIds.add(o.getDemandId());
            }
        }
        Map<Long, Demand> demands = byId(demandMapper, demandIds, Demand::getId);
        for (CreditEvent ev : list) {
            Long demandId = demandIdOf(ev, inspections, stages, orders);
            ev.setDemandId(demandId);
            Demand d = demandId == null ? null : demands.get(demandId);
            ev.setDemandTitle(d == null ? null : d.getTitle());
        }
    }

    private Long demandIdOf(CreditEvent ev,
                            Map<Long, Inspection> inspections,
                            Map<Long, WorkStage> stages,
                            Map<Long, Order> orders) {
        if (ev.getRefId() == null || ev.getRefType() == null) {
            return null;
        }
        return switch (ev.getRefType()) {
            case "DEMAND" -> ev.getRefId();
            case "ORDER" -> {
                Order o = orders.get(ev.getRefId());
                yield o == null ? null : o.getDemandId();
            }
            case "STAGE" -> demandIdOfStage(stages.get(ev.getRefId()), orders);
            case "INSPECTION" -> {
                Inspection i = inspections.get(ev.getRefId());
                yield i == null ? null : demandIdOfStage(stages.get(i.getStageId()), orders);
            }
            default -> null;
        };
    }

    private static Long demandIdOfStage(WorkStage ws, Map<Long, Order> orders) {
        if (ws == null || ws.getOrderId() == null) {
            return null;
        }
        Order o = orders.get(ws.getOrderId());
        return o == null ? null : o.getDemandId();
    }

    private <T> Map<Long, T> byId(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                  Set<Long> ids,
                                  java.util.function.Function<T, Long> idOf) {
        Map<Long, T> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<T> rows = mapper.selectBatchIds(ids);
        if (rows == null) {
            return map;
        }
        for (T row : rows) {
            Long id = idOf.apply(row);
            if (id != null) {
                map.put(id, row);
            }
        }
        return map;
    }
}
