package com.dsh.platform.dto;

import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DemandDtos {

    /** quantity 已废弃，件数以需求 quantity 为准；字段保留仅为兼容旧请求体。 */
    public record ProcessItem(Integer processNo, String processName, Integer quantity, String requirement) {}

    public record DeliveryPeriod(Integer percent, Integer qty, String text, String startAt, String endAt) {}

    public record PublishRequest(
            String title, String productName, String category, Integer quantity,
            String material, String tolerance, String surfaceTreatment,
            String aql, String certification, BigDecimal minYield, Integer minCreditScore,
            LocalDate deadlineHard, LocalDate deadlineFlexible,
            String deliveryAddress, String packaging, Integer multiProcess,
            String weightJson, Integer intentionDays, String remark,
            List<ProcessItem> processes,
            String inspectMode, BigDecimal inspectPrice, String generalTolerance, String partRevision,
            String extraJson, Long attachmentId,
            Long sourceDemandId,
            Integer deliveryTimes, List<DeliveryPeriod> deliveryPlan) {}

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
