package com.dsh.platform.domain.status;

import com.dsh.platform.common.BizException;

public enum DemandStatus {
    DRAFT,
    PENDING_AUDIT,
    PUBLISHED,
    RETURNED,
    /** 工厂思考期：是否参加、填实施方案+单价+分期交付，交 5% 保证金 */
    FACTORY_THINKING,
    /** 买家思考期：决定是否继续并按预估总价交 5% 保证金 */
    BUYER_THINKING,
    /** 旧流程状态，保留以兼容历史数据 */
    THINKING,
    REVIEWING,
    LOCKING,
    SOLUTION_GENERATED,
    SOLUTION_CONFIRMED,
    SOLUTION_SELECTED,
    CONTRACTED,
    IN_PRODUCTION,
    COMPLETED,
    CANCELLED,
    FLOW_FAILED;

    public static DemandStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRAFT;
        }
        try {
            return DemandStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BizException("未知需求状态: " + raw);
        }
    }
}
