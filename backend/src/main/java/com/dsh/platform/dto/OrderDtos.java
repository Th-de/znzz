package com.dsh.platform.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class OrderDtos {

    public record InspectRequest(String result, Integer sampleCount, Integer failCount,
                                 String keyDimensions, Boolean meetsRequirement, String remark) {}

    public record SignRequest(Boolean read, String sign, Long factoryTenantId) {}

    public record UploadContractRequest(Long attachmentId, Long factoryTenantId) {}

    public record SurveyRequest(Map<String, Integer> scores) {}

    public record ProgressRequest(Integer doneQty, String remark, Long attachmentId) {}

    public record TodoItem(String type, String title, String link, Integer count) {}

    public record ReplaceFactoryRequest(Integer processNo, Long factoryId) {}

    public record OrderListView(Long id, Long demandId, String title, String productName,
                                String factoryNames, BigDecimal totalAmount,
                                BigDecimal commissionAmount, String status) {}

    public record ComboItem(Long factoryId, String factoryName, Integer processNo,
                            String processName, Integer quantity, Object price, Object days) {}

    public record OrderDetailView(Long id, Long demandId, String title, String productName,
                                  Integer quantity, LocalDate deadlineHard, String status,
                                  BigDecimal totalAmount, BigDecimal commissionAmount,
                                  LocalDateTime createdAt, List<ComboItem> combo,
                                  List<String> flowSteps, Integer flowActive) {}
}
