package com.dsh.platform.dto;

import java.math.BigDecimal;

public class BiddingDtos {

    public record IntentionRequest(Long demandId, Integer processNo,
                                   Integer minQty, Integer maxQty,
                                   java.util.List<Long> deviceIds) {}

    public record IntentionStart(Long quotationId, boolean frozen, String payUrl) {}

    public record LockRequest(Long demandId, Integer processNo, BigDecimal price,
                              BigDecimal yieldRate, Integer promisedDays,
                              Integer minQty, Integer maxQty, String stageCurveJson) {}
}
