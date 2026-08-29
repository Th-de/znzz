package com.dsh.platform.domain.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 资金流水唯一写入入口。禁止在 Bidding/Flow/Order 里再插流水。
 * 每笔流水同步更新 account 余额/冻结。
 */
@Component
@RequiredArgsConstructor
public class FundLedger {

    private final FundFlowMapper fundFlowMapper;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final AccountService accountService;

    @Value("${dsh.fee.intention-fixed:1000}")
    private BigDecimal intentionFixed;

    public BigDecimal intentionAmount() {
        return intentionFixed;
    }

    public void freezeIntention(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        accountService.freeze(q.getTenantId(), intentionFixed);
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
            BigDecimal amt = freezeAmount(q, "INTENTION");
            accountService.unfreeze(q.getTenantId(), amt);
            write("INTENTION", "UNFREEZE", amt, q.getDemandId(), q.getTenantId(), null,
                    "INTENTION-UNFREEZE-" + q.getId());
            q.setIntentionStatus("RELEASED");
        } else if ("COVERED".equals(q.getIntentionStatus())) {
            // 意向金按单收一次：并入报名无冻结资金，直接释放
            q.setIntentionStatus("RELEASED");
        }
        q.setStatus("INVALID");
        quotationMapper.updateById(q);
    }

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
                accountService.unfreeze(q.getTenantId(), amount);
                write("DEPOSIT", "UNFREEZE", amount, demandId, q.getTenantId(), null,
                        "DEPOSIT-UNFREEZE-" + q.getId());
            }
            q.setDepositStatus("RELEASED");
            quotationMapper.updateById(q);
        }
    }

    /** 未锁价：冻结意向金罚没进平台暂存。 */
    public List<Quotation> forfeitUnlockedIntentions(Long demandId) {
        Long platformId = platformTenantId();
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getStatus, "INTENTION"));
        for (Quotation q : qs) {
            if ("FROZEN".equals(q.getIntentionStatus())) {
                accountService.impound(q.getTenantId(), platformId, intentionFixed);
                write("PENALTY", "OUT", intentionFixed, demandId, q.getTenantId(), null,
                        "INTENTION-NO-LOCK-" + q.getId());
                write("IMPOUND", "IN", intentionFixed, demandId, platformId, null,
                        "IMPOUND-IN-" + q.getId());
                q.setIntentionStatus("FORFEITED");
            }
            q.setStatus("INVALID");
            quotationMapper.updateById(q);
        }
        return qs;
    }

    /** 审核不通过：买家扣 500，均分给报名厂。 */
    public void forfeitIntention(Long demandId, Long buyerTenantId) {
        BigDecimal penalty = intentionFixed.multiply(new BigDecimal("0.5"));
        accountService.debit(buyerTenantId, penalty);
        write("PENALTY", "OUT", penalty, demandId, buyerTenantId, null,
                "INTENTION-FORFEIT-" + demandId);
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        List<Long> factories = qs.stream().map(Quotation::getTenantId).distinct().toList();
        if (factories.isEmpty() || penalty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal share = penalty.divide(BigDecimal.valueOf(factories.size()), 2, RoundingMode.DOWN);
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < factories.size(); i++) {
            BigDecimal part = (i == factories.size() - 1) ? penalty.subtract(given) : share;
            accountService.credit(factories.get(i), part);
            write("PENALTY", "IN", part, demandId, factories.get(i), null,
                    "INTENTION-COMP-" + demandId + "-" + factories.get(i));
            given = given.add(part);
        }
    }

    public void forfeitDeposit(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        if ("FROZEN".equals(q.getDepositStatus())) {
            BigDecimal amount = freezeAmount(q, "DEPOSIT");
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                Long platformId = platformTenantId();
                accountService.impound(q.getTenantId(), platformId, amount);
                write("PENALTY", "OUT", amount, q.getDemandId(), q.getTenantId(), null,
                        "DEPOSIT-FORFEIT-" + q.getId());
                write("IMPOUND", "IN", amount, q.getDemandId(), platformId, null,
                        "DEPOSIT-IMPOUND-" + q.getId());
            }
            q.setDepositStatus("FORFEITED");
        }
        q.setStatus("INVALID");
        quotationMapper.updateById(q);
    }

    /**
     * 落选退款：只解冻资金，不改 quotation.status（调用方已标 LOSE）。
     * 不可复用 unfreezeIntention，否则会把状态改成 INVALID。
     */
    public void refundLoser(Quotation q) {
        if (q == null || q.getId() == null) {
            return;
        }
        if ("FROZEN".equals(q.getIntentionStatus())) {
            BigDecimal amt = freezeAmount(q, "INTENTION");
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(q.getTenantId(), amt);
                write("INTENTION", "UNFREEZE", amt, q.getDemandId(), q.getTenantId(), null,
                        "LOSE-INTENTION-" + q.getId());
            }
            q.setIntentionStatus("RELEASED");
        }
        if ("FROZEN".equals(q.getDepositStatus())) {
            BigDecimal amount = freezeAmount(q, "DEPOSIT");
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(q.getTenantId(), amount);
                write("DEPOSIT", "UNFREEZE", amount, q.getDemandId(), q.getTenantId(), null,
                        "LOSE-DEPOSIT-" + q.getId());
            }
            q.setDepositStatus("RELEASED");
        }
        quotationMapper.updateById(q);
    }

    /** 买家思考期继续：按预估总价冻结买家保证金。 */
    public void freezeBuyerDeposit(Long demandId, Long buyerTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        accountService.freeze(buyerTenantId, amount);
        write("BUYER_DEPOSIT", "FREEZE", amount, demandId, buyerTenantId, null,
                "BUYER-DEPOSIT-FREEZE-" + demandId);
    }

    /** 买家保证金剩余冻结额（FREEZE - UNFREEZE/OUT）。 */
    public BigDecimal buyerDepositRemaining(Long demandId, Long buyerTenantId) {
        List<FundFlow> flows = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, demandId)
                .eq(FundFlow::getTenantId, buyerTenantId)
                .eq(FundFlow::getType, "BUYER_DEPOSIT"));
        BigDecimal remain = BigDecimal.ZERO;
        for (FundFlow f : flows) {
            if ("FREEZE".equals(f.getDirection())) {
                remain = remain.add(nvl(f.getAmount()));
            } else if ("UNFREEZE".equals(f.getDirection()) || "OUT".equals(f.getDirection())) {
                remain = remain.subtract(nvl(f.getAmount()));
            }
        }
        return remain.max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
    }

    /** 买家保证金退还（订单异常结束等）。 */
    public void refundBuyerDeposit(Long demandId, Long buyerTenantId) {
        BigDecimal remain = buyerDepositRemaining(demandId, buyerTenantId);
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            accountService.unfreeze(buyerTenantId, remain);
            write("BUYER_DEPOSIT", "UNFREEZE", remain, demandId, buyerTenantId, null,
                    "BUYER-DEPOSIT-UNFREEZE-" + demandId);
        }
    }

    /**
     * 尾款抵扣：把买家保证金（不超过 amount）转入平台托管，返回实际抵扣额。
     * 调用方只需再补足 amount - 返回值。
     */
    public BigDecimal applyBuyerDepositToEscrow(Long orderId, Long demandId, Long buyerTenantId,
                                                Long stageId, BigDecimal amount) {
        BigDecimal remain = buyerDepositRemaining(demandId, buyerTenantId);
        BigDecimal use = remain.min(amount == null ? BigDecimal.ZERO : amount);
        if (use.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        Long platformId = platformTenantId();
        accountService.deductFrozen(buyerTenantId, use);
        accountService.credit(platformId, use);
        write("BUYER_DEPOSIT", "OUT", use, demandId, buyerTenantId, orderId,
                "BUYER-DEPOSIT-OFFSET-" + stageId);
        write("ESCROW", "IN", use, demandId, platformId, orderId,
                "ESCROW-IN-DEPOSIT-" + stageId);
        return use;
    }

    public void freezeDeposit(Quotation q, BigDecimal amount) {
        if (q == null || q.getId() == null || amount == null) {
            return;
        }
        accountService.freeze(q.getTenantId(), amount);
        write("DEPOSIT", "FREEZE", amount, q.getDemandId(), q.getTenantId(), null,
                "DEPOSIT-FREEZE-" + q.getId());
        q.setDepositStatus("FROZEN");
        quotationMapper.updateById(q);
    }

    public void escrowStage(Long orderId, Long stageId, Long buyerTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long platformId = platformTenantId();
        accountService.debit(buyerTenantId, amount);
        accountService.credit(platformId, amount);
        write("ESCROW", "OUT", amount, null, buyerTenantId, orderId, "ESCROW-OUT-" + stageId);
        write("ESCROW", "IN", amount, null, platformId, orderId, "ESCROW-IN-" + stageId);
    }

    public void settleToFactory(Long orderId, Long factoryTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long platformId = platformTenantId();
        accountService.debit(platformId, amount);
        accountService.credit(factoryTenantId, amount);
        write("ESCROW", "OUT", amount, null, platformId, orderId,
                "SETTLE-ESCROW-OUT-" + orderId + "-" + factoryTenantId);
        write("PAYMENT", "IN", amount, null, factoryTenantId, orderId,
                "SETTLE-IN-" + orderId + "-" + factoryTenantId);
    }

    public void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long platformId = platformTenantId();
        BigDecimal available = frozenDepositOf(demandId, factoryTenantId);
        BigDecimal take = amount.min(available);
        if (take.compareTo(BigDecimal.ZERO) > 0) {
            accountService.deductFrozen(factoryTenantId, take);
            accountService.credit(platformId, take);
            write("DEPOSIT", "OUT", take, demandId, factoryTenantId, orderId,
                    "COMMISSION-DEPOSIT-" + orderId + "-" + factoryTenantId);
            write("COMMISSION", "IN", take, demandId, platformId, orderId,
                    "COMMISSION-IN-" + orderId + "-" + factoryTenantId);
        }
        BigDecimal leftover = available.subtract(take);
        if (leftover.compareTo(BigDecimal.ZERO) > 0) {
            accountService.unfreeze(factoryTenantId, leftover);
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

    public Long platformTenantId() {
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
