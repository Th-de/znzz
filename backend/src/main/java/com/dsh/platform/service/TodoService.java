package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.dto.OrderDtos.TodoItem;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final WorkStageMapper workStageMapper;
    private final ContractMapper contractMapper;
    private final SolutionMapper solutionMapper;
    private final QuotationMapper quotationMapper;

    public List<TodoItem> mine() {
        String role = UserContext.role();
        if ("BUYER".equals(role)) {
            return buyerTodos();
        }
        if ("FACTORY".equals(role)) {
            return factoryTodos();
        }
        return List.of();
    }

    private List<TodoItem> buyerTodos() {
        List<TodoItem> out = new ArrayList<>();
        List<Demand> demands = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, UserContext.tenantId()));
        Set<Long> demandIds = demands.stream().map(Demand::getId).collect(Collectors.toSet());
        if (demandIds.isEmpty()) {
            return out;
        }
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>().in(Order::getDemandId, demandIds));
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        if (!orderIds.isEmpty()) {
            List<WorkStage> pay = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                    .in(WorkStage::getOrderId, orderIds)
                    .eq(WorkStage::getStatus, "PASS")
                    .in(WorkStage::getEscrowStatus, "NONE", "PENDING_PAY"));
            if (!pay.isEmpty()) {
                WorkStage first = pay.get(0);
                out.add(new TodoItem("PAY_STAGE", "有 " + pay.size() + " 笔阶段款待支付托管",
                        "/buyer/order/" + first.getOrderId(), pay.size()));
            }
            List<Contract> unsigned = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                    .in(Contract::getOrderId, orderIds)
                    .ne(Contract::getStatus, "SIGNED"));
            long needSign = unsigned.stream().filter(c -> c.getAttachmentId() == null
                    || c.getBuyerSign() == null || c.getBuyerSign().isBlank()).count();
            if (needSign > 0) {
                out.add(new TodoItem("SIGN_CONTRACT", "有 " + needSign + " 份合同待你上传或签名",
                        "/buyer/order/" + unsigned.get(0).getOrderId(), (int) needSign));
            }
            for (Order o : orders) {
                if (!"IN_PRODUCTION".equals(o.getStatus())) {
                    continue;
                }
                List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                        .eq(WorkStage::getOrderId, o.getId()));
                boolean allReady = !stages.isEmpty() && stages.stream().allMatch(s ->
                        "PASS".equals(s.getStatus()) && "HELD".equals(s.getEscrowStatus()));
                if (allReady) {
                    out.add(new TodoItem("ACCEPT_ORDER", "订单#" + o.getId() + " 可完工确认",
                            "/buyer/order/" + o.getId(), 1));
                }
            }
        }
        long returned = demands.stream().filter(d -> "RETURNED".equals(d.getStatus())).count();
        if (returned > 0) {
            Demand d = demands.stream().filter(x -> "RETURNED".equals(x.getStatus())).findFirst().orElse(null);
            out.add(new TodoItem("RETURNED_DEMAND", "有 " + returned + " 条需求被退回，请修改后重提",
                    "/buyer/publish?id=" + (d == null ? "" : d.getId()), (int) returned));
        }
        long thinking = demands.stream().filter(d -> "THINKING".equals(d.getStatus())).count();
        if (thinking > 0) {
            Demand d = demands.stream().filter(x -> "THINKING".equals(x.getStatus())).findFirst().orElse(null);
            out.add(new TodoItem("THINKING_DECIDE", "有 " + thinking + " 条需求在思考期，请决定继续或取消",
                    "/buyer/demand/" + (d == null ? "" : d.getId()), (int) thinking));
        }
        List<Demand> waiting = demands.stream().filter(d -> "SOLUTION_GENERATED".equals(d.getStatus())).toList();
        for (Demand d : waiting) {
            Long n = solutionMapper.selectCount(new LambdaQueryWrapper<Solution>()
                    .eq(Solution::getDemandId, d.getId())
                    .eq(Solution::getStatus, "ACTIVE"));
            if (n != null && n > 0) {
                out.add(new TodoItem("PICK_SOLUTION", "需求「" + d.getTitle() + "」可选择方案",
                        "/buyer/solutions/" + d.getId(), 1));
            }
        }
        return out;
    }

    private List<TodoItem> factoryTodos() {
        List<TodoItem> out = new ArrayList<>();
        Long tid = UserContext.tenantId();
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getTenantId, tid));
        long pendingPay = qs.stream().filter(q -> "PENDING_PAY".equals(q.getIntentionStatus())).count();
        if (pendingPay > 0) {
            out.add(new TodoItem("PAY_INTENTION", "有 " + pendingPay + " 笔意向金待支付",
                    "/factory/quotations", (int) pendingPay));
        }
        Set<Long> lockIds = qs.stream()
                .filter(q -> "INTENTION".equals(q.getStatus()) && "FROZEN".equals(q.getIntentionStatus()))
                .map(Quotation::getDemandId).collect(Collectors.toSet());
        if (!lockIds.isEmpty()) {
            Long n = demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                    .in(Demand::getId, lockIds)
                    .eq(Demand::getStatus, "LOCKING"));
            if (n != null && n > 0) {
                out.add(new TodoItem("LOCK_QUOTE", "有 " + n + " 条需求可锁定报价",
                        "/factory/demands", n.intValue()));
            }
        }
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getTenantId, tid)
                .ne(Contract::getStatus, "SIGNED"));
        long needSign = contracts.stream().filter(c -> c.getAttachmentId() != null).count();
        if (needSign > 0) {
            out.add(new TodoItem("SIGN_CONTRACT", "有 " + needSign + " 份合同待你签署",
                    "/factory/stages", (int) needSign));
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, tid));
        long startCount = stages.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        if (startCount > 0) {
            out.add(new TodoItem("START_WORK", "有 " + startCount + " 张工单待开工",
                    "/factory/stages", (int) startCount));
        }
        long producing = stages.stream().filter(s -> "IN_PRODUCTION".equals(s.getStatus())).count();
        if (producing > 0) {
            out.add(new TodoItem("REPORT_PROGRESS", "有 " + producing + " 张工单在生产中，请上报进度",
                    "/factory/stages", (int) producing));
        }
        return out;
    }
}
