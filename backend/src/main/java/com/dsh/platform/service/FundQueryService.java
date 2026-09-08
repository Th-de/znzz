package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Account;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Order;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import com.dsh.platform.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FundQueryService {

    private final FundFlowMapper fundFlowMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final AccountService accountService;

    public Map<String, Object> overview() {
        List<FundFlow> all = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .orderByDesc(FundFlow::getId));
        BigDecimal intentionIn = sum(all, "INTENTION", "FREEZE");
        BigDecimal intentionOut = sum(all, "INTENTION", "UNFREEZE");
        BigDecimal depositIn = sum(all, "DEPOSIT", "FREEZE");
        BigDecimal depositOut = sum(all, "DEPOSIT", "UNFREEZE").add(sum(all, "DEPOSIT", "OUT"));
        BigDecimal escrowHeld = sum(all, "ESCROW", "IN").subtract(sum(all, "ESCROW", "OUT"));
        BigDecimal commission = sum(all, "COMMISSION", "IN");
        Long platformId = platformTenantId();
        Account platform = accountService.get(platformId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intentionFrozenNet", intentionIn.subtract(intentionOut).max(BigDecimal.ZERO));
        m.put("depositFrozenNet", depositIn.subtract(depositOut).max(BigDecimal.ZERO));
        m.put("platformEscrow", escrowHeld.max(BigDecimal.ZERO));
        m.put("commissionTotal", commission);
        m.put("platformBalance", platform.getBalance());
        m.put("platformFrozen", platform.getFrozen());
        return m;
    }

    /** 最近 N 天每日流水总额（绝对值合计），用于趋势图。 */
    public List<Map<String, Object>> trend(int days) {
        int n = Math.max(days, 1);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(n - 1L);
        List<FundFlow> all = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .ge(FundFlow::getCreatedAt, start.atStartOfDay())
                .orderByAsc(FundFlow::getCreatedAt));
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            byDay.put(d, BigDecimal.ZERO);
        }
        for (FundFlow f : all) {
            if (f.getCreatedAt() == null || f.getAmount() == null) {
                continue;
            }
            LocalDate day = f.getCreatedAt().toLocalDate();
            byDay.computeIfPresent(day, (k, v) -> v.add(f.getAmount().abs()));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().toString());
            row.put("amount", e.getValue());
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> byTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BizException("企业不能为空");
        }
        Enterprise e = enterpriseMapper.selectById(tenantId);
        if (e == null) {
            throw new BizException("企业不存在");
        }
        Account acc = accountService.get(tenantId);
        acc.setEnterpriseName(e.getName());
        acc.setEnterpriseType(e.getType());
        List<FundFlow> flows = enrich(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getTenantId, tenantId)
                .orderByDesc(FundFlow::getId)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", tenantId);
        m.put("enterpriseName", e.getName());
        m.put("enterpriseType", e.getType());
        m.put("account", acc);
        m.put("flows", flows);
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

    public List<FundFlow> mine(Long tenantId) {
        return enrich(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getTenantId, tenantId)
                .orderByDesc(FundFlow::getId)));
    }

    public List<FundFlow> all() {
        return enrich(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .orderByDesc(FundFlow::getId)));
    }

    private List<FundFlow> enrich(List<FundFlow> list) {
        Set<Long> demandIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (FundFlow f : list) {
            if (f.getDemandId() != null) {
                demandIds.add(f.getDemandId());
            } else if (f.getOrderId() != null) {
                orderIds.add(f.getOrderId());
            }
        }
        Map<Long, Order> orders = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<Order> os = orderMapper.selectBatchIds(orderIds);
            if (os != null) {
                for (Order o : os) {
                    orders.put(o.getId(), o);
                    if (o.getDemandId() != null) {
                        demandIds.add(o.getDemandId());
                    }
                }
            }
        }
        Map<Long, Demand> demands = new HashMap<>();
        if (!demandIds.isEmpty()) {
            List<Demand> ds = demandMapper.selectBatchIds(demandIds);
            if (ds != null) {
                for (Demand d : ds) {
                    demands.put(d.getId(), d);
                }
            }
        }
        for (FundFlow f : list) {
            Enterprise e = enterpriseMapper.selectById(f.getTenantId());
            if (e != null) {
                f.setEnterpriseName(e.getName());
            }
            Long demandId = f.getDemandId();
            if (demandId == null && f.getOrderId() != null) {
                Order o = orders.get(f.getOrderId());
                demandId = o == null ? null : o.getDemandId();
                f.setDemandId(demandId);
            }
            Demand d = demandId == null ? null : demands.get(demandId);
            f.setDemandTitle(d == null ? null : d.getTitle());
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
