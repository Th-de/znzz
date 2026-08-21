package com.dsh.platform.domain.pay;

import com.dsh.platform.domain.fund.FundLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LedgerChannel implements PaymentChannel {

    private final FundLedger fundLedger;

    @Override
    public void escrowStage(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        fundLedger.escrowStage(orderId, stageId, buyerTenantId, amount);
    }

    @Override
    public void settleToFactory(Long orderId, Long factoryTenantId, BigDecimal amount) {
        fundLedger.settleToFactory(orderId, factoryTenantId, amount);
    }

    @Override
    public void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount) {
        fundLedger.takeCommission(orderId, demandId, factoryTenantId, amount);
    }
}
