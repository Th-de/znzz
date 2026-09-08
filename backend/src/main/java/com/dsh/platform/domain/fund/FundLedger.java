package com.dsh.platform.domain.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        releaseIntentionAfterDeposit(q);
    }

    /** 冻结履约保证金后释放该厂本单意向金，不改变报价/中标状态。 */
    private void releaseIntentionAfterDeposit(Quotation q) {
        if (q.getDemandId() == null || q.getTenantId() == null) {
            return;
        }
        List<Quotation> mine = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, q.getDemandId())
                .eq(Quotation::getTenantId, q.getTenantId()));
        boolean paid = false;
        for (Quotation each : mine) {
            if ("FROZEN".equals(each.getIntentionStatus()) && !paid) {
                String key = "INTENTION-UNFREEZE-DEPOSIT-" + each.getDemandId() + "-" + each.getTenantId();
                Long exists = fundFlowMapper.selectCount(new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getIdempotentNo, key));
                BigDecimal amt = freezeAmount(each, "INTENTION");
                if ((exists == null || exists == 0) && amt.compareTo(BigDecimal.ZERO) > 0) {
                    accountService.unfreeze(each.getTenantId(), amt);
                    write("INTENTION", "UNFREEZE", amt, each.getDemandId(), each.getTenantId(), null, key);
                }
                paid = true;
            }
            if ("FROZEN".equals(each.getIntentionStatus()) || "COVERED".equals(each.getIntentionStatus())) {
                each.setIntentionStatus("RELEASED");
                quotationMapper.updateById(each);
            }
        }
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

    /**
     * 质检费入运营账户。
     */
    public void collectInspectFee(Long orderId, Long demandId, Long payerTenantId, Long stageId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long platformId = platformTenantId();
        accountService.debit(payerTenantId, amount);
        accountService.credit(platformId, amount);
        write("INSPECT_FEE", "OUT", amount, demandId, payerTenantId, orderId, "INSPECT-FEE-OUT-" + stageId);
        write("INSPECT_FEE", "IN", amount, demandId, platformId, orderId, "INSPECT-FEE-IN-" + stageId);
    }

    /**
     * 让步赔付：从工厂剩余冻结保证金全额划给买家。不足则失败，不部分划转。
     */
    public BigDecimal payConcessionToBuyer(Long orderId, Long demandId, Long factoryTenantId,
                                           Long buyerTenantId, Long stageId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal available = frozenDepositOf(demandId, factoryTenantId);
        if (available.compareTo(amount) < 0) {
            throw new BizException("该厂剩余保证金不足以按规则赔付，不能让步，请返工或关闭本段");
        }
        accountService.deductFrozen(factoryTenantId, amount);
        accountService.credit(buyerTenantId, amount);
        write("DEPOSIT", "OUT", amount, demandId, factoryTenantId, orderId,
                "CONCESSION-DEPOSIT-" + stageId);
        write("PENALTY", "IN", amount, demandId, buyerTenantId, orderId,
                "CONCESSION-BUYER-" + stageId);
        return amount;
    }

    /**
     * 佣金从已托管工钱划转（钱已在平台），不占用工厂履约保证金。剩余保证金退回工厂。
     */
    public void takeCommission(Long orderId, Long demandId, Long factoryTenantId, BigDecimal amount) {
        Long platformId = platformTenantId();
        BigDecimal take = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        if (take.compareTo(BigDecimal.ZERO) > 0) {
            accountService.debit(platformId, take);
            accountService.credit(platformId, take);
            write("ESCROW", "OUT", take, demandId, platformId, orderId,
                    "COMMISSION-ESCROW-OUT-" + orderId + "-" + factoryTenantId);
            write("COMMISSION", "IN", take, demandId, platformId, orderId,
                    "COMMISSION-IN-" + orderId + "-" + factoryTenantId);
        }
        BigDecimal leftover = frozenDepositOf(demandId, factoryTenantId);
        if (leftover.compareTo(BigDecimal.ZERO) > 0) {
            accountService.unfreeze(factoryTenantId, leftover);
            write("DEPOSIT", "UNFREEZE", leftover, demandId, factoryTenantId, orderId,
                    "DEPOSIT-RELEASE-" + orderId + "-" + factoryTenantId);
        }
        boolean penalized = hasDepositOut(demandId, factoryTenantId);
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryTenantId)
                .eq(Quotation::getDepositStatus, "FROZEN"));
        for (Quotation q : qs) {
            q.setDepositStatus(penalized ? "DEDUCTED" : "RELEASED");
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

    private boolean hasDepositOut(Long demandId, Long factoryTenantId) {
        Long n = fundFlowMapper.selectCount(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, demandId)
                .eq(FundFlow::getTenantId, factoryTenantId)
                .eq(FundFlow::getType, "DEPOSIT")
                .eq(FundFlow::getDirection, "OUT"));
        return n != null && n > 0;
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

    /**
     * 从买家冻结保证金划给工厂（赔偿），幂等。
     */
    public void payBuyerDepositToFactory(Long demandId, Long buyerTenantId, Long factoryTenantId,
                                         Long orderId, BigDecimal amount, String tag) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || factoryTenantId == null) {
            return;
        }
        BigDecimal remain = buyerDepositRemaining(demandId, buyerTenantId);
        BigDecimal use = remain.min(amount);
        if (use.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        accountService.deductFrozen(buyerTenantId, use);
        accountService.credit(factoryTenantId, use);
        write("BUYER_DEPOSIT", "OUT", use, demandId, buyerTenantId, orderId,
                "BUYER-DEPOSIT-COMP-" + tag + "-" + factoryTenantId);
        write("PENALTY", "IN", use, demandId, factoryTenantId, orderId,
                "FACTORY-COMP-" + tag + "-" + factoryTenantId);
    }

    /** 按权重把买家保证金池分给各厂，余数给最后一个。 */
    public void splitBuyerDepositToFactories(Long demandId, Long buyerTenantId, Long orderId,
                                             BigDecimal pool, Map<Long, Integer> weights, String tag) {
        if (pool == null || pool.compareTo(BigDecimal.ZERO) <= 0 || weights == null || weights.isEmpty()) {
            return;
        }
        int sum = weights.values().stream().mapToInt(v -> v == null ? 0 : Math.max(v, 0)).sum();
        if (sum <= 0) {
            return;
        }
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(weights.entrySet());
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < entries.size(); i++) {
            Long fid = entries.get(i).getKey();
            int w = entries.get(i).getValue() == null ? 0 : Math.max(entries.get(i).getValue(), 0);
            BigDecimal part = (i == entries.size() - 1)
                    ? pool.subtract(given)
                    : pool.multiply(BigDecimal.valueOf(w)).divide(BigDecimal.valueOf(sum), 2, RoundingMode.DOWN);
            if (part.compareTo(BigDecimal.ZERO) < 0) {
                part = BigDecimal.ZERO;
            }
            payBuyerDepositToFactory(demandId, buyerTenantId, fid, orderId, part, tag);
            given = given.add(part.max(BigDecimal.ZERO));
        }
    }

    /** 工厂冻结保证金全额划给买家。 */
    public void payFactoryDepositToBuyer(Long demandId, Long factoryTenantId, Long buyerTenantId, Long orderId, String tag) {
        BigDecimal available = frozenDepositOf(demandId, factoryTenantId);
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        accountService.deductFrozen(factoryTenantId, available);
        accountService.credit(buyerTenantId, available);
        write("DEPOSIT", "OUT", available, demandId, factoryTenantId, orderId,
                "DEPOSIT-TO-BUYER-" + tag + "-" + factoryTenantId);
        write("PENALTY", "IN", available, demandId, buyerTenantId, orderId,
                "BUYER-FROM-FACTORY-" + tag + "-" + factoryTenantId);
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryTenantId)
                .eq(Quotation::getDepositStatus, "FROZEN"));
        for (Quotation q : qs) {
            q.setDepositStatus("DEDUCTED");
            quotationMapper.updateById(q);
        }
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
