package com.dsh.platform.domain.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import com.dsh.platform.mapper.QuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 资金流水唯一写入入口。禁止在 Bidding/Flow/Order 里再插流水。
 */
@Component
@RequiredArgsConstructor
public class FundLedger {

    private final FundFlowMapper fundFlowMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Value("${dsh.fee.intention-fixed:1000}")
    private BigDecimal intentionFixed;

    public BigDecimal intentionAmount() {
        return intentionFixed;
    }

    public void freezeIntention(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        write("INTENTION", "FREEZE", intentionFixed, q.getDemandId(), q.getTenantId(), null,
                "INTENTION-FREEZE-" + q.getId());
        q.setIntentionStatus("FROZEN");
        quotationMapper.updateById(q);
    }

    public void unfreezeIntention(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        if ("FROZEN".equals(q.getIntentionStatus())) {
            write("INTENTION", "UNFREEZE", freezeAmount(q, "INTENTION"), q.getDemandId(), q.getTenantId(), null,
                    "INTENTION-UNFREEZE-" + q.getId());
            q.setIntentionStatus("RELEASED");
        }
        q.setStatus("INVALID");
        quotationMapper.updateById(q);
    }

    /** 运营退回 / 买家取消需求：作废报名并解冻已冻意向金（幂等）。 */
    public void unfreezeIntentionsOfDemand(Long demandId) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        for (Quotation q : qs) {
            unfreezeIntention(q);
        }
    }

    public void unfreezeDepositsOfDemand(Long demandId) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getDepositStatus, "FROZEN"));
        for (Quotation q : qs) {
            BigDecimal amount = freezeAmount(q, "DEPOSIT");
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                write("DEPOSIT", "UNFREEZE", amount, demandId, q.getTenantId(), null,
                        "DEPOSIT-UNFREEZE-" + q.getId());
            }
            q.setDepositStatus("RELEASED");
            quotationMapper.updateById(q);
        }
    }

    /** 审核不通过：买家扣 500，均分给报名厂。 */
    public void forfeitIntention(Long demandId, Long buyerTenantId) {
        BigDecimal penalty = intentionFixed.multiply(new BigDecimal("0.5"));
        write("PENALTY", "OUT", penalty, demandId, buyerTenantId, null,
                "INTENTION-FORFEIT-" + demandId);
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        List<Long> factories = qs.stream().map(Quotation::getTenantId).distinct().toList();
        if (factories.isEmpty() || penalty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal share = penalty.divide(BigDecimal.valueOf(factories.size()), 2, java.math.RoundingMode.DOWN);
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < factories.size(); i++) {
            BigDecimal part = (i == factories.size() - 1) ? penalty.subtract(given) : share;
            write("PENALTY", "IN", part, demandId, factories.get(i), null,
                    "INTENTION-COMP-" + demandId + "-" + factories.get(i));
            given = given.add(part);
        }
    }

    /** 工厂保证金期取消：保证金不退。 */
    public void forfeitDeposit(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        if ("FROZEN".equals(q.getDepositStatus())) {
            BigDecimal amount = freezeAmount(q, "DEPOSIT");
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                write("PENALTY", "OUT", amount, q.getDemandId(), q.getTenantId(), null,
                        "DEPOSIT-FORFEIT-" + q.getId());
            }
            q.setDepositStatus("FORFEITED");
        }
        q.setStatus("INVALID");
        quotationMapper.updateById(q);
    }

    public void freezeDeposit(Quotation q, BigDecimal amount) {
        if (q == null || q.getId() == null || amount == null) {
            return;
        }
        write("DEPOSIT", "FREEZE", amount, q.getDemandId(), q.getTenantId(), null,
                "DEPOSIT-FREEZE-" + q.getId());
        q.setDepositStatus("FROZEN");
        quotationMapper.updateById(q);
    }

    /** 买家付阶段款：买家 ESCROW OUT，平台 ESCROW IN。工厂余额不变。 */
    public void escrowStage(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        write("ESCROW", "OUT", amount, null, buyerTenantId, orderId, "ESCROW-OUT-" + stageId);
        write("ESCROW", "IN", amount, null, platformTenantId(), orderId, "ESCROW-IN-" + stageId);
    }

    /** 完工结算：平台托管出账，工厂 PAYMENT IN。 */
    public void settleToFactory(Long orderId, Long factoryTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        write("ESCROW", "OUT", amount, null, platformTenantId(), orderId,
                "SETTLE-ESCROW-OUT-" + orderId + "-" + factoryTenantId);
        write("PAYMENT", "IN", amount, null, factoryTenantId, orderId,
                "SETTLE-IN-" + orderId + "-" + factoryTenantId);
    }

    /** 佣金从该厂保证金扣，入平台。 */
    public void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal available = frozenDepositOf(demandId, factoryTenantId);
        BigDecimal take = amount.min(available);
        if (take.compareTo(BigDecimal.ZERO) > 0) {
            write("DEPOSIT", "OUT", take, demandId, factoryTenantId, orderId,
                    "COMMISSION-DEPOSIT-" + orderId + "-" + factoryTenantId);
            write("COMMISSION", "IN", take, demandId, platformTenantId(), orderId,
                    "COMMISSION-IN-" + orderId + "-" + factoryTenantId);
        }
        BigDecimal leftover = available.subtract(take);
        if (leftover.compareTo(BigDecimal.ZERO) > 0) {
            write("DEPOSIT", "UNFREEZE", leftover, demandId, factoryTenantId, orderId,
                    "DEPOSIT-RELEASE-" + orderId + "-" + factoryTenantId);
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryTenantId)
                .eq(Quotation::getDepositStatus, "FROZEN"));
        for (Quotation q : qs) {
            q.setDepositStatus(take.compareTo(BigDecimal.ZERO) > 0 ? "DEDUCTED" : "RELEASED");
            quotationMapper.updateById(q);
        }
    }

    private BigDecimal frozenDepositOf(Long demandId, Long factoryTenantId) {
        List<FundFlow> freezes = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, demandId)
                .eq(FundFlow::getTenantId, factoryTenantId)
                .eq(FundFlow::getType, "DEPOSIT")
                .eq(FundFlow::getDirection, "FREEZE"));
        BigDecimal in = BigDecimal.ZERO;
        for (FundFlow f : freezes) {
            in = in.add(nvl(f.getAmount()));
        }
        List<FundFlow> outs = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, demandId)
                .eq(FundFlow::getTenantId, factoryTenantId)
                .eq(FundFlow::getType, "DEPOSIT")
                .in(FundFlow::getDirection, "UNFREEZE", "OUT"));
        BigDecimal out = BigDecimal.ZERO;
        for (FundFlow f : outs) {
            out = out.add(nvl(f.getAmount()));
        }
        return in.subtract(out).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
    }

    private Long platformTenantId() {
        Enterprise e = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, "PLATFORM")
                .last("limit 1"));
        return e == null ? 1L : e.getId();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal freezeAmount(Quotation q, String type) {
        FundFlow last = fundFlowMapper.selectOne(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, q.getDemandId())
                .eq(FundFlow::getTenantId, q.getTenantId())
                .eq(FundFlow::getType, type)
                .eq(FundFlow::getDirection, "FREEZE")
                .orderByDesc(FundFlow::getId)
                .last("limit 1"));
        if (last != null && last.getAmount() != null) {
            return last.getAmount();
        }
        return "INTENTION".equals(type) ? intentionFixed : BigDecimal.ZERO;
    }

    private void write(String type, String direction, BigDecimal amount,
                       Long demandId, Long tenantId, Long orderId, String idempotentNo) {
        Long exists = fundFlowMapper.selectCount(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getIdempotentNo, idempotentNo));
        if (exists != null && exists > 0) {
            return;
        }
        FundFlow f = new FundFlow();
        f.setDemandId(demandId);
        f.setOrderId(orderId);
        f.setTenantId(tenantId);
        f.setType(type);
        f.setDirection(direction);
        f.setAmount(amount);
        f.setStatus("SUCCESS");
        f.setIdempotentNo(idempotentNo);
        fundFlowMapper.insert(f);
    }
}
