package com.dsh.platform.dto;

import com.dsh.platform.entity.WorkStage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class OrderDtos {

    public record InspectRequest(String result, Integer sampleCount, Integer failCount,
                                 Integer criticalFailCount, Integer generalFailCount,
                                 Integer deliveredQty, String keyDimensions, Boolean meetsRequirement,
                                 String remark, java.math.BigDecimal actualYield, Boolean quantityOk) {}

    public record DecisionRequest(String action, Integer reworkHours, Integer reworkDays) {}

    public record SignRequest(Boolean read, String sign, Long factoryTenantId) {}

    public record UploadContractRequest(Long attachmentId, Long factoryTenantId) {}

    public record SurveyRequest(Map<String, Integer> scores) {}

    public record ProgressRequest(Integer doneQty, String remark, Long attachmentId) {}

    public record DeliverRequest(Integer deliveredQty) {}

    public record TodoItem(String type, String title, String link, Integer count) {}

    public record TodoAckRequest(String type, Long bizId) {}

    public record FactoryDemandJob(Long demandId, Long orderId, String demandTitle, String productName,
                                   String detail, Integer progress, BigDecimal totalAmount, String status,
                                   Boolean contractSigned, List<WorkStage> periods) {}

    public record ReplaceFactoryRequest(Integer processNo, Long factoryId, Long fromFactoryId) {}

    public record OrderListView(Long id, Long demandId, String title, String productName,
                                String factoryNames, BigDecimal totalAmount,
                                BigDecimal commissionAmount, String status,
                                LocalDateTime createdAt) {}

    public record ComboItem(Long factoryId, String factoryName, Integer processNo,
                            String processName, Integer quantity, Object price, Object days,
                            Integer minQty, Integer maxQty) {}

    public record OrderDetailView(Long id, Long demandId, String title, String productName,
                                  Integer quantity, LocalDate deadlineHard, String status,
                                  BigDecimal totalAmount, BigDecimal commissionAmount,
                                  LocalDateTime createdAt, List<ComboItem> combo,
                                  List<String> flowSteps, Integer flowActive,
                                  LocalDateTime contractIssueEndAt, LocalDateTime contractSignEndAt,
                                  Boolean canBuyerCancel, Boolean canFactoryCancel) {}
}
