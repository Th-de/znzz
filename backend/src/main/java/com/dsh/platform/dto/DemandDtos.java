package com.dsh.platform.dto;

import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DemandDtos {

    /**
     * quantity 已废弃，件数以需求 quantity 为准；字段保留仅为兼容旧请求体。
     * 前端可能提交空行，空名称的工序由 DemandService 过滤，这里不做非空校验。
     */
    public record ProcessItem(
            @Min(value = 1, message = "工序序号从 1 开始") Integer processNo,
            @Size(max = 64, message = "工序名称最多 64 字") String processName,
            Integer quantity,
            @Size(max = 500, message = "工序要求最多 500 字") String requirement) {}

    public record DeliveryPeriod(
            @Min(0) @Max(100) Integer percent,
            @Min(0) Integer qty,
            @Size(max = 200) String text,
            String startAt, String endAt) {}

    /**
     * 发单请求。这里只做「形状」校验（非空/范围/长度）；行业规则（材料、公差、AQL 等的组合要求）仍在 DemandService 里。
     */
    public record PublishRequest(
            @NotBlank(message = "请填写需求标题") @Size(max = 128) String title,
            @NotBlank(message = "请填写产品名称") @Size(max = 128) String productName,
            String category,
            @NotNull(message = "请填写数量") @Min(value = 1, message = "数量必须大于 0") Integer quantity,
            String material, String tolerance, String surfaceTreatment,
            String aql, String certification,
            @DecimalMin(value = "0", message = "最低良率不能为负") BigDecimal minYield,
            @Min(value = 0, message = "最低信用分不能为负") Integer minCreditScore,
            LocalDate deadlineHard, LocalDate deadlineFlexible,
            String deliveryAddress, String packaging, Integer multiProcess,
            String weightJson,
            @Min(value = 1, message = "意向期至少 1 天") @Max(value = 30, message = "意向期最长 30 天") Integer intentionDays,
            @Size(max = 2000, message = "备注最多 2000 字") String remark,
            @Valid List<ProcessItem> processes,
            String inspectMode,
            @DecimalMin(value = "0", message = "质检费不能为负") BigDecimal inspectPrice,
            String generalTolerance, String partRevision,
            String extraJson, Long attachmentId,
            Long sourceDemandId,
            @Min(value = 1, message = "分期交付次数须在 1~10 之间") @Max(value = 10, message = "分期交付次数须在 1~10 之间") Integer deliveryTimes,
            @Valid List<DeliveryPeriod> deliveryPlan) {}

    public record ReturnRequest(String reason) {}

    public record DemandDetailView(Demand demand, List<Process> processes, List<Attachment> attachments,
                                   java.util.Map<String, Object> quoteStats,
                                   java.util.Map<String, Object> buyer) {}

    public record AuditRequest(String result, String reason) {}

    public record CancelRequest(String reason) {}

    public record CancelStats(int last90Days, boolean warn) {}

    public record DecideRequest(String action) {}

    public record CancelWithProofRequest(String reason, String proof) {}
}
