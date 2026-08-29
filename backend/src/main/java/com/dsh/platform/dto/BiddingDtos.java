package com.dsh.platform.dto;

import java.math.BigDecimal;
import java.util.List;

public class BiddingDtos {

    /** 单工序报名项：承接量区间 + 匹配该工序的设备 */
    public record IntentionItem(Integer processNo, Integer minQty, Integer maxQty,
                                List<Long> deviceIds) {}

    /** 意向报名：支持一次多工序（items），意向金按单收一次；兼容旧的单工序字段 */
    public record IntentionRequest(Long demandId, Integer processNo,
                                   Integer minQty, Integer maxQty,
                                   List<Long> deviceIds,
                                   List<IntentionItem> items) {}

    public record IntentionStart(Long quotationId, boolean frozen, String payUrl) {}

    /** 工厂思考期填报项：单价（承接量沿用意向期，不可改） */
    public record CommitItem(Integer processNo, BigDecimal unitPrice,
                             BigDecimal yieldRate, Integer promisedDays) {}

    /** 工厂思考期填报：实施方案 + 每期交付内容 + 各工序单价，冻结总报价 5% 保证金 */
    public record CommitRequest(Long demandId, String planText,
                                List<String> deliveryPlan,
                                List<CommitItem> items) {}

    /** 旧流程锁价请求（兼容保留） */
    public record LockRequest(Long demandId, Integer processNo, BigDecimal price,
                              BigDecimal yieldRate, Integer promisedDays,
                              Integer minQty, Integer maxQty, String stageCurveJson) {}
}
