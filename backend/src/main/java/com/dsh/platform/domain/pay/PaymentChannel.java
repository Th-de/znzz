package com.dsh.platform.domain.pay;

import java.math.BigDecimal;

/**
 * 支付通道。D 用内部账本，F 再加支付宝沙箱。
 */
public interface PaymentChannel {

    /** 内部账本立刻托管；沙箱则只下单，返回支付链接。 */
    default EscrowStart startEscrow(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        escrowStage(orderId, stageId, buyerTenantId, amount);
        return new EscrowStart(true, null);
    }

    /** 内部账本立刻冻结意向金；沙箱则只下单，返回支付链接。 */
    default EscrowStart startIntention(Long quotationId, BigDecimal amount) {
        return new EscrowStart(true, null);
    }

    void escrowStage(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount);

    void settleToFactory(Long orderId, Long factoryTenantId, BigDecimal amount);

    void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount);
}
