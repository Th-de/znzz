package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.*;
import com.dsh.platform.mapper.*;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DemandPanoramaService {

    private final DemandMapper demandMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final QuotationMapper quotationMapper;
    private final FundFlowMapper fundFlowMapper;
    private final OrderMapper orderMapper;
    private final WorkStageMapper workStageMapper;
    private final StageProgressLogMapper stageProgressLogMapper;
    private final DeviceService deviceService;
    private final AccountService accountService;

    public Map<String, Object> of(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        String role = UserContext.role();
        if ("BUYER".equals(role) && !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能看自己的需求");
        }
        Enterprise buyer = enterpriseMapper.selectById(d.getTenantId());
        Account buyerAcc = accountService.get(d.getTenantId());

        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .orderByAsc(Quotation::getProcessNo)
                .orderByDesc(Quotation::getId));

        List<Map<String, Object>> bids = new ArrayList<>();
        for (Quotation q : qs) {
            Enterprise factory = enterpriseMapper.selectById(q.getTenantId());
            List<Long> ids = deviceService.parseIds(q.getDeviceIdsJson());
            List<Device> devices = deviceService.byIds(ids);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quotationId", q.getId());
            row.put("processNo", q.getProcessNo());
            row.put("factoryId", q.getTenantId());
            row.put("factoryName", factory == null ? "" : factory.getName());
            row.put("status", q.getStatus());
            row.put("intentionStatus", q.getIntentionStatus());
            row.put("depositStatus", q.getDepositStatus());
            row.put("price", q.getPrice());
            row.put("minQty", q.getMinQty());
            row.put("maxQty", q.getMaxQty());
            row.put("devices", devices);
            bids.add(row);
        }

        List<FundFlow> flows = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getDemandId, demandId)
                .orderByDesc(FundFlow::getId));
        for (FundFlow f : flows) {
            Enterprise e = enterpriseMapper.selectById(f.getTenantId());
            if (e != null) {
                f.setEnterpriseName(e.getName());
            }
        }
        BigDecimal fundSubtotal = flows.stream()
                .map(f -> f.getAmount() == null ? BigDecimal.ZERO : f.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getDemandId, demandId)
                .orderByDesc(Order::getId)
                .last("limit 1"));

        List<Map<String, Object>> stages = new ArrayList<>();
        int qtySum = 0;
        int doneSum = 0;
        if (order != null) {
            List<WorkStage> wsList = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                    .eq(WorkStage::getOrderId, order.getId())
                    .orderByAsc(WorkStage::getProcessNo));
            for (WorkStage ws : wsList) {
                Enterprise factory = enterpriseMapper.selectById(ws.getTenantId());
                StageProgressLog last = stageProgressLogMapper.selectOne(new LambdaQueryWrapper<StageProgressLog>()
                        .eq(StageProgressLog::getStageId, ws.getId())
                        .orderByDesc(StageProgressLog::getId)
                        .last("limit 1"));
                int qty = ws.getQuantity() == null ? 0 : ws.getQuantity();
                int done = last == null || last.getDoneQty() == null ? 0 : last.getDoneQty();
                qtySum += qty;
                doneSum += done;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stageId", ws.getId());
                row.put("processNo", ws.getProcessNo());
                row.put("processName", ws.getProcessName());
                row.put("factoryName", factory == null ? "" : factory.getName());
                row.put("quantity", qty);
                row.put("doneQty", done);
                row.put("progress", ws.getActualProgress() == null ? 0 : ws.getActualProgress());
                row.put("status", ws.getStatus());
                row.put("promisedDate", ws.getPromisedDate());
                stages.add(row);
            }
        }
        int percent = qtySum <= 0 ? 0 : (int) Math.round(doneSum * 100.0 / qtySum);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demand", d);
        m.put("buyer", Map.of(
                "id", buyer == null ? d.getTenantId() : buyer.getId(),
                "name", buyer == null ? "" : buyer.getName(),
                "creditCode", buyer == null || buyer.getCreditCode() == null ? "" : buyer.getCreditCode(),
                "address", buyer == null || buyer.getAddress() == null ? "" : buyer.getAddress(),
                "contactName", buyer == null || buyer.getContactName() == null ? "" : buyer.getContactName(),
                "balance", buyerAcc.getBalance(),
                "frozen", buyerAcc.getFrozen()
        ));
        m.put("stage", d.getStatus());
        m.put("bids", bids);
        m.put("funds", flows);
        m.put("fundSubtotal", fundSubtotal);
        m.put("orderId", order == null ? null : order.getId());
        m.put("orderStatus", order == null ? null : order.getStatus());
        m.put("stages", stages);
        m.put("progressPercent", percent);
        return m;
    }
}
