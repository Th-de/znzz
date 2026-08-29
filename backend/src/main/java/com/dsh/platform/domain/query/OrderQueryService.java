package com.dsh.platform.domain.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.dto.OrderDtos.ComboItem;
import com.dsh.platform.dto.OrderDtos.OrderDetailView;
import com.dsh.platform.dto.OrderDtos.OrderListView;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper orderMapper;
    private final DemandMapper demandMapper;
    private final WorkStageMapper workStageMapper;
    private final ContractMapper contractMapper;
    private final SurveyMapper surveyMapper;
    private final QuotationMapper quotationMapper;
    private final SolutionMapper solutionMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;

    public List<OrderListView> listForCurrentUser() {
        return loadOrders().stream().map(this::toListView).toList();
    }

    public OrderDetailView detail(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        assertCanView(o);
        Demand d = demandMapper.selectById(o.getDemandId());
        Solution s = o.getSolutionId() == null ? null : solutionMapper.selectById(o.getSolutionId());
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId));
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, orderId));
        return new OrderDetailView(
                o.getId(),
                o.getDemandId(),
                d == null ? "" : d.getTitle(),
                d == null ? "" : d.getProductName(),
                d == null ? null : d.getQuantity(),
                d == null ? null : d.getDeadlineHard(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getCommissionAmount(),
                o.getCreatedAt(),
                comboOf(s),
                flowSteps(contracts),
                flowActive(o.getStatus(), contracts, stages)
        );
    }

    public List<WorkStage> stages(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        String role = UserContext.role();
        if ("BUYER".equals(role)) {
            Demand d = demandMapper.selectById(o.getDemandId());
            if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
                throw new BizException(403, "无权查看该订单工单");
            }
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, orderId)
                .orderByAsc(WorkStage::getProcessNo));
        if ("FACTORY".equals(role)) {
            stages = stages.stream()
                    .filter(ws -> UserContext.tenantId().equals(ws.getTenantId()))
                    .toList();
        }
        enrich(stages);
        return stages;
    }

    public List<WorkStage> myStages() {
        if (!"FACTORY".equals(UserContext.role())) {
            throw new BizException(403, "仅工厂可查看自己的工单");
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getTenantId, UserContext.tenantId())
                .orderByDesc(WorkStage::getId));
        enrich(stages);
        return stages;
    }

    private List<Order> loadOrders() {
        String role = UserContext.role();
        if ("FACTORY".equals(role)) {
            List<WorkStage> mine = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                    .eq(WorkStage::getTenantId, UserContext.tenantId()));
            Set<Long> ids = mine.stream().map(WorkStage::getOrderId).collect(Collectors.toSet());
            List<Quotation> wins = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                    .eq(Quotation::getTenantId, UserContext.tenantId())
                    .eq(Quotation::getStatus, "WIN"));
            Set<Long> demandIds = wins.stream().map(Quotation::getDemandId).collect(Collectors.toSet());
            if (!demandIds.isEmpty()) {
                orderMapper.selectList(new LambdaQueryWrapper<Order>()
                                .in(Order::getDemandId, demandIds))
                        .forEach(o -> ids.add(o.getId()));
            }
            if (ids.isEmpty()) {
                return List.of();
            }
            return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .in(Order::getId, ids)
                    .orderByDesc(Order::getId));
        }
        if ("BUYER".equals(role)) {
            List<Demand> ds = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                    .eq(Demand::getTenantId, UserContext.tenantId()));
            Set<Long> demandIds = ds.stream().map(Demand::getId).collect(Collectors.toSet());
            if (demandIds.isEmpty()) {
                return List.of();
            }
            return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .in(Order::getDemandId, demandIds)
                    .orderByDesc(Order::getId));
        }
        return orderMapper.selectList(new LambdaQueryWrapper<Order>().orderByDesc(Order::getId));
    }

    private OrderListView toListView(Order o) {
        Demand d = demandMapper.selectById(o.getDemandId());
        Solution s = o.getSolutionId() == null ? null : solutionMapper.selectById(o.getSolutionId());
        String factories = factoryNames(s);
        if (factories.isBlank()) {
            factories = contractFactoryNames(o.getId());
        }
        return new OrderListView(
                o.getId(),
                o.getDemandId(),
                d == null ? "" : d.getTitle(),
                d == null ? "" : d.getProductName(),
                factories,
                o.getTotalAmount(),
                o.getCommissionAmount(),
                o.getStatus(),
                o.getCreatedAt()
        );
    }

    private String factoryNames(Solution s) {
        if (s == null) {
            return "";
        }
        Set<String> names = new LinkedHashSet<>();
        for (ComboItem item : comboOf(s)) {
            if (item.factoryName() != null && !item.factoryName().isBlank()) {
                names.add(item.factoryName());
            }
        }
        return String.join("、", names);
    }

    private String contractFactoryNames(Long orderId) {
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId));
        Set<String> names = new LinkedHashSet<>();
        for (Contract c : list) {
            Enterprise e = enterpriseMapper.selectById(c.getTenantId());
            names.add(e == null ? ("厂" + c.getTenantId()) : e.getName());
        }
        return String.join("、", names);
    }

    private List<ComboItem> comboOf(Solution s) {
        if (s == null) {
            return List.of();
        }
        List<ComboItem> out = new ArrayList<>();
        for (Map<String, Object> item : readCombo(s.getFinalComboJson())) {
            Long factoryId = asLong(item.get("factoryId"));
            String factoryName = item.get("factoryName") == null ? null : item.get("factoryName").toString();
            if ((factoryName == null || factoryName.isBlank()) && factoryId != null) {
                Enterprise e = enterpriseMapper.selectById(factoryId);
                factoryName = e == null ? ("厂" + factoryId) : e.getName();
            }
            Object qty = item.get("quantity");
            out.add(new ComboItem(
                    factoryId,
                    factoryName,
                    asInt(item.get("processNo")),
                    item.get("processName") == null ? "工序" : item.get("processName").toString(),
                    qty == null ? null : asInt(qty),
                    item.get("price"),
                    item.get("days")
            ));
        }
        return out;
    }

    private List<String> flowSteps(List<Contract> contracts) {
        String contractLabel = "合同（未传）";
        if (!contracts.isEmpty()) {
            if (contracts.stream().allMatch(c -> "SIGNED".equals(c.getStatus()))) {
                contractLabel = "合同（已确认）";
            } else if (contracts.stream().anyMatch(c -> "PENDING_REVIEW".equals(c.getStatus()))) {
                contractLabel = "合同（待审）";
            } else if (contracts.stream().anyMatch(c -> c.getAttachmentId() != null)) {
                contractLabel = "合同（待签）";
            }
        }
        return List.of("选定方案", contractLabel, "开工", "质检托管", "完工结算");
    }

    private int flowActive(String orderStatus, List<Contract> contracts, List<WorkStage> stages) {
        if ("COMPLETED".equals(orderStatus)) {
            return 4;
        }
        boolean allSigned = !contracts.isEmpty()
                && contracts.stream().allMatch(c -> "SIGNED".equals(c.getStatus()));
        if (!allSigned) {
            return 1;
        }
        if (stages.isEmpty()) {
            return 2;
        }
        boolean inspecting = stages.stream().anyMatch(ws ->
                List.of("PENDING_INSPECTION", "INSPECTING", "PASS", "FAIL", "COMPLETED").contains(ws.getStatus())
                        || List.of("HELD", "SETTLED", "PENDING_PAY").contains(ws.getEscrowStatus()));
        if (inspecting) {
            return 3;
        }
        return 2;
    }

    private void assertCanView(Order o) {
        String role = UserContext.role();
        if ("OPERATOR".equals(role) || "SUPER_ADMIN".equals(role) || "INSPECTION".equals(role)) {
            return;
        }
        if ("BUYER".equals(role)) {
            Demand d = demandMapper.selectById(o.getDemandId());
            if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
                throw new BizException(403, "无权查看该订单");
            }
            return;
        }
        if ("FACTORY".equals(role)) {
            Long n = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getOrderId, o.getId())
                    .eq(Contract::getTenantId, UserContext.tenantId()));
            if (n == null || n == 0) {
                throw new BizException(403, "无权查看该订单");
            }
            return;
        }
        throw new BizException(403, "无权查看该订单");
    }

    private void enrich(List<WorkStage> stages) {
        for (WorkStage ws : stages) {
            Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getOrderId, ws.getOrderId())
                    .eq(Contract::getTenantId, ws.getTenantId())
                    .last("limit 1"));
            ws.setContractSigned(c != null && "SIGNED".equals(c.getStatus()));
            Long n = surveyMapper.selectCount(new LambdaQueryWrapper<Survey>()
                    .eq(Survey::getStageId, ws.getId())
                    .eq(Survey::getRole, UserContext.role())
                    .eq(Survey::getTenantId, UserContext.tenantId()));
            ws.setSurveyed(n != null && n > 0);
            Enterprise e = ws.getTenantId() == null ? null : enterpriseMapper.selectById(ws.getTenantId());
            ws.setFactoryName(e == null ? ("厂" + ws.getTenantId()) : e.getName());
        }
    }

    private List<Map<String, Object>> readCombo(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer asInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
