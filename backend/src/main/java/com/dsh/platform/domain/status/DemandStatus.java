package com.dsh.platform.domain.status;

import com.dsh.platform.common.BizException;

public enum DemandStatus {
    DRAFT,
    PENDING_AUDIT,
    PUBLISHED,
    RETURNED,
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
