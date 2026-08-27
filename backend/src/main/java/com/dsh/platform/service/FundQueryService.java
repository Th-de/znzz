package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Account;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FundQueryService {

    private final FundFlowMapper fundFlowMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandMapper demandMapper;
    private final AccountService accountService;

    public Map<String, Object> overview() {
        List<FundFlow> all = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .orderByDesc(FundFlow::getId));
        BigDecimal intentionIn = sum(all, "INTENTION", "FREEZE");
        BigDecimal intentionOut = sum(all, "INTENTION", "UNFREEZE");
        BigDecimal depositIn = sum(all, "DEPOSIT", "FREEZE");
        BigDecimal depositOut = sum(all, "DEPOSIT", "UNFREEZE").add(sum(all, "DEPOSIT", "OUT"));
        BigDecimal escrowHeld = sum(all, "ESCROW", "IN").subtract(sum(all, "ESCROW", "OUT"));
        BigDecimal impound = sum(all, "IMPOUND", "IN").subtract(sum(all, "IMPOUND", "OUT"));
        BigDecimal commission = sum(all, "COMMISSION", "IN");
        Long platformId = platformTenantId();
        Account platform = accountService.get(platformId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intentionFrozenNet", intentionIn.subtract(intentionOut).max(BigDecimal.ZERO));
        m.put("depositFrozenNet", depositIn.subtract(depositOut).max(BigDecimal.ZERO));
        m.put("escrowHeld", escrowHeld.max(BigDecimal.ZERO));
        m.put("impoundBalance", impound.max(BigDecimal.ZERO));
        m.put("commissionTotal", commission);
        m.put("platformBalance", platform.getBalance());
        m.put("platformFrozen", platform.getFrozen());
        return m;
    }

    public List<Map<String, Object>> byDemand() {
        List<FundFlow> all = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .isNotNull(FundFlow::getDemandId)
                .orderByDesc(FundFlow::getId));
        Map<Long, List<FundFlow>> grouped = new LinkedHashMap<>();
        for (FundFlow f : all) {
            grouped.computeIfAbsent(f.getDemandId(), k -> new ArrayList<>()).add(f);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<FundFlow>> e : grouped.entrySet()) {
            Demand d = demandMapper.selectById(e.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("demandId", e.getKey());
            row.put("demandTitle", d == null ? "" : d.getTitle());
            row.put("demandStatus", d == null ? "" : d.getStatus());
            row.put("flows", enrich(e.getValue()));
            row.put("subtotal", e.getValue().stream()
                    .map(f -> f.getAmount() == null ? BigDecimal.ZERO : f.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            out.add(row);
        }
        return out;
    }

    public List<Account> accounts() {
        return accountService.listAll();
    }

    public List<FundFlow> impoundFlows() {
        return enrich(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getType, "IMPOUND")
                .orderByDesc(FundFlow::getId)));
    }

    /** 预留：暂存资金处置（暂不开放实际划转） */
    public void disposeImpound(Long flowId, String action, String remark) {
        if (flowId == null) {
            throw new BizException("请指定流水");
        }
        throw new BizException("暂存处置接口已预留，尚未开放实际划转：" + (action == null ? "" : action));
    }

    private List<FundFlow> enrich(List<FundFlow> list) {
        for (FundFlow f : list) {
            Enterprise e = enterpriseMapper.selectById(f.getTenantId());
            if (e != null) {
                f.setEnterpriseName(e.getName());
            }
        }
        return list;
    }

    private BigDecimal sum(List<FundFlow> all, String type, String direction) {
        BigDecimal s = BigDecimal.ZERO;
        for (FundFlow f : all) {
            if (type.equals(f.getType()) && direction.equals(f.getDirection()) && f.getAmount() != null) {
                s = s.add(f.getAmount());
            }
        }
        return s;
    }

    private Long platformTenantId() {
        Enterprise e = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, "PLATFORM")
                .last("limit 1"));
        return e == null ? 1L : e.getId();
    }
}
