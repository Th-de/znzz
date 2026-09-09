package com.dsh.platform.domain.status;

import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Demand;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 需求状态唯一入口。业务侧禁止直接 setStatus。
 */
@Component
public class DemandStateMachine {

    private static final Map<DemandStatus, Set<DemandStatus>> ALLOWED = new EnumMap<>(DemandStatus.class);

    static {
        ALLOWED.put(DemandStatus.DRAFT, EnumSet.of(DemandStatus.PUBLISHED, DemandStatus.PENDING_AUDIT, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.PENDING_AUDIT, EnumSet.of(DemandStatus.PUBLISHED, DemandStatus.RETURNED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.PUBLISHED, EnumSet.of(DemandStatus.FACTORY_THINKING,
                DemandStatus.THINKING, DemandStatus.FLOW_FAILED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.RETURNED, EnumSet.of(DemandStatus.PENDING_AUDIT, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.FACTORY_THINKING, EnumSet.of(DemandStatus.BUYER_THINKING, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.BUYER_THINKING, EnumSet.of(DemandStatus.SOLUTION_GENERATED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.THINKING, EnumSet.of(DemandStatus.LOCKING, DemandStatus.REVIEWING, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.REVIEWING, EnumSet.of(DemandStatus.LOCKING, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.LOCKING, EnumSet.of(DemandStatus.SOLUTION_GENERATED, DemandStatus.FLOW_FAILED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.SOLUTION_GENERATED, EnumSet.of(DemandStatus.SOLUTION_CONFIRMED, DemandStatus.FLOW_FAILED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.SOLUTION_CONFIRMED, EnumSet.of(DemandStatus.SOLUTION_SELECTED, DemandStatus.FLOW_FAILED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.SOLUTION_SELECTED, EnumSet.of(DemandStatus.CONTRACTED, DemandStatus.CANCELLED, DemandStatus.FLOW_FAILED));
        ALLOWED.put(DemandStatus.CONTRACTED, EnumSet.of(DemandStatus.IN_PRODUCTION, DemandStatus.COMPLETED, DemandStatus.CANCELLED));
        ALLOWED.put(DemandStatus.IN_PRODUCTION, EnumSet.of(DemandStatus.COMPLETED));
        ALLOWED.put(DemandStatus.COMPLETED, EnumSet.noneOf(DemandStatus.class));
        ALLOWED.put(DemandStatus.CANCELLED, EnumSet.noneOf(DemandStatus.class));
        ALLOWED.put(DemandStatus.FLOW_FAILED, EnumSet.noneOf(DemandStatus.class));
    }

    public void transit(Demand demand, DemandStatus target) {
        DemandStatus from = DemandStatus.of(demand.getStatus());
        Set<DemandStatus> next = ALLOWED.getOrDefault(from, EnumSet.noneOf(DemandStatus.class));
        if (!next.contains(target)) {
            throw new BizException("需求状态不可从 " + from + " 变更为 " + target);
        }
        demand.setStatus(target.name());
    }
}
