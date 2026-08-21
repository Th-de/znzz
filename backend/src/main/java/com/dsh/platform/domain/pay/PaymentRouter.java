package com.dsh.platform.domain.pay;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Primary
@Component
@RequiredArgsConstructor
public class PaymentRouter implements PaymentChannel {

    private final LedgerChannel ledgerChannel;
    private final AlipaySandboxChannel alipaySandboxChannel;

    @Override
    public EscrowStart startEscrow(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        if (alipaySandboxChannel.ready()) {
            return alipaySandboxChannel.startPay(stageId, amount, "阶段工钱#" + stageId);
        }
        return ledgerChannel.startEscrow(orderId, stageId, buyerTenantId, amount);
    }

    @Override
    public EscrowStart startIntention(Long quotationId, BigDecimal amount) {
        if (alipaySandboxChannel.ready()) {
            return alipaySandboxChannel.startIntentionPay(quotationId, amount);
        }
        return new EscrowStart(true, null);
    }

    @Override
    public void escrowStage(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        ledgerChannel.escrowStage(orderId, stageId, buyerTenantId, amount);
    }

    @Override
    public void settleToFactory(Long orderId, Long factoryTenantId, BigDecimal amount) {
        ledgerChannel.settleToFactory(orderId, factoryTenantId, amount);
    }

    @Override
    public void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount) {
        ledgerChannel.takeCommission(orderId, demandId, factoryTenantId, amount);
    }
}
