package com.dsh.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class BiddingDtos {

    /** 报名项：承接零件数区间（processNo 兼容旧请求，忽略）。min<=max 与是否覆盖需求量在 BiddingService 判定。 */
    public record IntentionItem(Integer processNo,
                                @Min(value = 1, message = "最小承接量必须大于 0") Integer minQty,
                                @Min(value = 1, message = "最大承接量必须大于 0") Integer maxQty,
                                List<Long> deviceIds) {}

    /** 意向报名：一厂一需求一行，对比 demand.quantity。区间可放在顶层字段或 items[0]，二者至少一处有值。 */
    public record IntentionRequest(@NotNull(message = "缺少需求 ID") Long demandId,
                                   Integer processNo,
                                   @Min(value = 1, message = "最小承接量必须大于 0") Integer minQty,
                                   @Min(value = 1, message = "最大承接量必须大于 0") Integer maxQty,
                                   List<Long> deviceIds,
                                   @Valid List<IntentionItem> items) {}

    public record IntentionStart(Long quotationId, boolean frozen, String payUrl) {}

    /** 工厂思考期填报项：单价（承接量沿用意向期，不可改） */
    public record CommitItem(Integer processNo,
                             @DecimalMin(value = "0.01", message = "单价必须大于 0") BigDecimal unitPrice,
                             BigDecimal yieldRate, Integer promisedDays) {}

    /** 工厂思考期填报：实施方案 + 每期交付内容 + 各工序单价，冻结总报价 5% 保证金 */
    public record CommitRequest(@NotNull(message = "缺少需求 ID") Long demandId,
                                @NotBlank(message = "请填写实施方案") @Size(max = 4000, message = "实施方案最多 4000 字") String planText,
                                List<String> deliveryPlan,
                                @NotEmpty(message = "请填写该品单价") @Valid List<CommitItem> items) {}

    /** 旧流程锁价请求（兼容保留） */
    public record LockRequest(Long demandId, Integer processNo, BigDecimal price,
                              BigDecimal yieldRate, Integer promisedDays,
                              Integer minQty, Integer maxQty, String stageCurveJson) {}
}
