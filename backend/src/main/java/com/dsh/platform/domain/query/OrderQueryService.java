package com.dsh.platform.domain.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.dto.OrderDtos.FactoryDemandJob;
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
import com.dsh.platform.domain.inspect.AqlPlans;
import com.dsh.platform.domain.inspect.InspectPrices;
import com.dsh.platform.domain.inspect.InspectRules;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.mapper.InspectionMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final InspectionMapper inspectionMapper;
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
        boolean created = "CREATED".equals(o.getStatus());
        boolean inSign = created && o.getContractSignEndAt() != null;
        boolean canBuyer = "BUYER".equals(UserContext.role()) && inSign;
        boolean canFactory = false;
        if ("FACTORY".equals(UserContext.role()) && inSign) {
            canFactory = contracts.stream().anyMatch(c ->
                    UserContext.tenantId().equals(c.getTenantId())
                            && !"SIGNED".equals(c.getStatus())
                            && !"CANCELLED".equals(c.getStatus()));
        }
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
                comboOf(s, o.getDemandId()),
                flowSteps(contracts),
                flowActive(o.getStatus(), contracts, stages),
                o.getContractIssueEndAt(),
                o.getContractSignEndAt(),
                canBuyer,
                canFactory
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

    public List<FactoryDemandJob> myDemandJobs() {
        List<WorkStage> stages = myStages();
        Map<Long, List<WorkStage>> byOrder = stages.stream()
                .collect(Collectors.groupingBy(WorkStage::getOrderId, java.util.LinkedHashMap::new, Collectors.toList()));
        List<FactoryDemandJob> jobs = new ArrayList<>();
        for (Map.Entry<Long, List<WorkStage>> e : byOrder.entrySet()) {
            Order o = orderMapper.selectById(e.getKey());
            Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
            List<WorkStage> periods = new ArrayList<>(e.getValue());
            periods.sort((a, b) -> {
                int pa = a.getPeriodNo() == null ? 0 : a.getPeriodNo();
                int pb = b.getPeriodNo() == null ? 0 : b.getPeriodNo();
                if (pa != pb) {
                    return Integer.compare(pa, pb);
                }
                int na = a.getProcessNo() == null ? 0 : a.getProcessNo();
                int nb = b.getProcessNo() == null ? 0 : b.getProcessNo();
                return Integer.compare(na, nb);
            });
            BigDecimal total = periods.stream()
                    .map(ws -> ws.getAmount() == null ? BigDecimal.ZERO : ws.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int progress = 0;
            if (!periods.isEmpty()) {
                progress = (int) Math.round(periods.stream()
                        .mapToInt(ws -> ws.getActualProgress() == null ? 0 : ws.getActualProgress())
                        .average().orElse(0));
            }
            boolean signed = periods.stream().anyMatch(ws -> Boolean.TRUE.equals(ws.getContractSigned()));
            String status = rollupStatus(periods, signed);
            String detail = d == null ? "" : ((d.getProductName() == null ? "" : d.getProductName())
                    + (d.getCategory() == null || d.getCategory().isBlank() ? "" : " / " + d.getCategory())
                    + (d.getQuantity() == null ? "" : " / " + d.getQuantity() + "件"));
            Long demandId = d == null ? null : d.getId();
            for (WorkStage ws : periods) {
                ws.setDemandId(demandId);
                ws.setDemandTitle(d == null ? "" : d.getTitle());
            }
            jobs.add(new FactoryDemandJob(
                    demandId,
                    e.getKey(),
                    d == null ? "" : d.getTitle(),
                    d == null ? "" : d.getProductName(),
                    detail,
                    progress,
                    total,
                    status,
                    signed,
                    periods));
        }
        return jobs;
    }

    private String rollupStatus(List<WorkStage> periods, boolean signed) {
        if (!signed) {
            return "PENDING_SIGN";
        }
        if (periods.stream().anyMatch(ws -> "FAIL".equals(ws.getStatus()))) {
            return "FAIL";
        }
        if (periods.stream().anyMatch(ws -> "IN_PRODUCTION".equals(ws.getStatus())
                || (ws.getReworkCount() != null && ws.getReworkCount() > 0 && "IN_PRODUCTION".equals(ws.getStatus())))) {
            return "IN_PRODUCTION";
        }
        if (periods.stream().anyMatch(ws -> "WAITING_OPEN".equals(ws.getStatus()))) {
            return "WAITING_OPEN";
        }
        if (periods.stream().allMatch(ws -> "CLOSED".equals(ws.getStatus()) || "CANCELLED".equals(ws.getStatus()))) {
            return "CLOSED";
        }
        if (periods.stream().allMatch(ws -> List.of("PASS", "COMPLETED", "CLOSED").contains(ws.getStatus()))) {
            return "COMPLETED";
        }
        if (periods.stream().anyMatch(ws -> "PENDING_INSPECT_PAY".equals(ws.getStatus()))) {
            return "PENDING_INSPECT_PAY";
        }
        if (periods.stream().anyMatch(ws -> List.of("PENDING_INSPECTION", "PENDING_REVIEW").contains(ws.getStatus()))) {
            return "PENDING_INSPECTION";
        }
        return periods.get(0).getStatus();
    }

    /** 质检台：仅工厂进度完成并提交质检后的工单，生产中的工单质检方不可见。 */
    public List<WorkStage> inspectQueue() {
        String role = UserContext.role();
        if (!"OPERATOR".equals(role) && !"SUPER_ADMIN".equals(role) && !"INSPECTION".equals(role)) {
            throw new BizException(403, "无权查看质检台");
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .in(WorkStage::getStatus, "PENDING_INSPECT_PAY", "PENDING_INSPECTION", "PENDING_REVIEW", "INSPECTING", "PASS", "FAIL", "CLOSED", "COMPLETED")
                .orderByDesc(WorkStage::getUpdatedAt)
                .orderByDesc(WorkStage::getId));
        if ("INSPECTION".equals(role)) {
            stages = stages.stream().filter(ws -> !"PENDING_INSPECT_PAY".equals(ws.getStatus())).toList();
        }
        enrich(stages);
        fillInspectSheet(stages);
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
        for (ComboItem item : comboOf(s, null)) {
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

    private List<ComboItem> comboOf(Solution s, Long demandId) {
        if (s == null) {
            return List.of();
        }
        Map<Long, Acc> byFactory = new LinkedHashMap<>();
        int orphan = 0;
        for (Map<String, Object> item : readCombo(s.getFinalComboJson())) {
            Long factoryId = asLong(item.get("factoryId"));
            String factoryName = item.get("factoryName") == null ? null : item.get("factoryName").toString();
            if ((factoryName == null || factoryName.isBlank()) && factoryId != null) {
                Enterprise e = enterpriseMapper.selectById(factoryId);
                factoryName = e == null ? ("厂" + factoryId) : e.getName();
            }
            Integer qty = asInt(item.get("quantity"));
            Integer minQty = asInt(item.get("minQty"));
            Integer maxQty = asInt(item.get("maxQty"));
            Integer[] range = bidRange(demandId, factoryId);
            if (minQty == null) {
                minQty = range[0];
            }
            if (maxQty == null) {
                maxQty = range[1];
            }
            Long key = factoryId != null ? factoryId : --orphan;
            Acc acc = byFactory.get(key);
            if (acc == null) {
                acc = new Acc(factoryId, factoryName, minQty, maxQty);
                byFactory.put(key, acc);
            }
            acc.quantity += qty == null ? 0 : qty;
            acc.price = acc.price.add(asDecimal(item.get("price")) == null ? BigDecimal.ZERO : asDecimal(item.get("price")));
            if (minQty != null) {
                acc.minQty = acc.minQty == null ? minQty : Math.min(acc.minQty, minQty);
            }
            if (maxQty != null) {
                acc.maxQty = acc.maxQty == null ? maxQty : Math.max(acc.maxQty, maxQty);
            }
            if (acc.factoryName == null || acc.factoryName.isBlank()) {
                acc.factoryName = factoryName;
            }
        }
        List<ComboItem> out = new ArrayList<>();
        for (Acc acc : byFactory.values()) {
            out.add(new ComboItem(
                    acc.factoryId,
                    acc.factoryName,
                    null,
                    null,
                    acc.quantity,
                    acc.price,
                    null,
                    acc.minQty,
                    acc.maxQty
            ));
        }
        return out;
    }

    private static final class Acc {
        final Long factoryId;
        String factoryName;
        int quantity;
        BigDecimal price = BigDecimal.ZERO;
        Integer minQty;
        Integer maxQty;

        Acc(Long factoryId, String factoryName, Integer minQty, Integer maxQty) {
            this.factoryId = factoryId;
            this.factoryName = factoryName;
            this.minQty = minQty;
            this.maxQty = maxQty;
        }
    }

    private Integer[] bidRange(Long demandId, Long factoryId) {
        Integer min = null;
        Integer max = null;
        if (demandId == null || factoryId == null) {
            return new Integer[] {null, null};
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryId));
        for (Quotation q : qs) {
            if (q.getMinQty() != null) {
                min = min == null ? q.getMinQty() : Math.min(min, q.getMinQty());
            }
            if (q.getMaxQty() != null) {
                max = max == null ? q.getMaxQty() : Math.max(max, q.getMaxQty());
            }
        }
        return new Integer[] {min, max};
    }

    private List<String> flowSteps(List<Contract> contracts) {
        String contractLabel = "合同（未传）";
        if (!contracts.isEmpty()) {
            if (contracts.stream().allMatch(c -> "SIGNED".equals(c.getStatus()))) {
                contractLabel = "合同（已确认）";
            } else if (contracts.stream().anyMatch(c -> "PENDING_REVIEW".equals(c.getStatus()))) {
                contractLabel = "合同（待买家确认）";
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
                List.of("PENDING_INSPECTION", "PENDING_REVIEW", "INSPECTING", "PASS", "FAIL", "CLOSED", "COMPLETED").contains(ws.getStatus())
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
            Order o = orderMapper.selectById(ws.getOrderId());
            if (o != null) {
                ws.setDemandId(o.getDemandId());
            }
            Integer[] range = bidRange(o == null ? null : o.getDemandId(), ws.getTenantId());
            ws.setMinQty(range[0]);
            ws.setMaxQty(range[1]);
            fillDecisionFlags(ws);
            fillPeriodWindow(ws);
        }
        fillInspectionFlags(stages);
    }

    private void fillInspectionFlags(List<WorkStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        Set<Long> ids = stages.stream().map(WorkStage::getId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> has = inspectionMapper.selectList(new LambdaQueryWrapper<Inspection>()
                        .in(Inspection::getStageId, ids)
                        .select(Inspection::getStageId))
                .stream()
                .map(Inspection::getStageId)
                .collect(Collectors.toSet());
        for (WorkStage ws : stages) {
            ws.setHasInspection(has.contains(ws.getId()));
        }
    }

    private void fillPeriodWindow(WorkStage ws) {
        LocalDateTime now = LocalDateTime.now();
        boolean signed = Boolean.TRUE.equals(ws.getContractSigned());
        boolean started = ws.getPeriodStart() == null || !now.isBefore(ws.getPeriodStart());
        ws.setWindowOpen(signed && started && isOpenable(ws.getStatus()));
        int no = ws.getPeriodNo() == null ? 0 : ws.getPeriodNo();
        ws.setPeriodLabel(no > 0 ? ("第" + no + "期") : (ws.getProcessName() == null ? "本期" : ws.getProcessName()));
        if (("WAITING_OPEN".equals(ws.getStatus()) || "PENDING".equals(ws.getStatus())) && signed && started) {
            ws.setStatus("IN_PRODUCTION");
            ws.setUpdatedAt(now);
            workStageMapper.updateById(ws);
        } else if (("PENDING".equals(ws.getStatus()) || "WAITING_OPEN".equals(ws.getStatus())) && signed && !started) {
            if (!"WAITING_OPEN".equals(ws.getStatus())) {
                ws.setStatus("WAITING_OPEN");
                workStageMapper.updateById(ws);
            }
        }
    }

    private static boolean isOpenable(String status) {
        return status == null
                || "PENDING".equals(status)
                || "WAITING_OPEN".equals(status)
                || "IN_PRODUCTION".equals(status);
    }

    private void fillDecisionFlags(WorkStage ws) {
        applyInspectPlan(ws, null);
        ws.setCanConcede(false);
        ws.setCanRework(false);
        ws.setCanClose(false);
        ws.setBranchCode(null);
        if (!"FAIL".equals(ws.getStatus())) {
            return;
        }
        Inspection ins = inspectionMapper.selectOne(new LambdaQueryWrapper<Inspection>()
                .eq(Inspection::getStageId, ws.getId())
                .orderByDesc(Inspection::getId)
                .last("limit 1"));
        Map<String, Object> report = readExtra(ins == null ? null : ins.getReportJson());
        int dc = asInt(report.get("criticalFailCount")) == null ? 0 : asInt(report.get("criticalFailCount"));
        int dg = asInt(report.get("generalFailCount")) == null ? 0 : asInt(report.get("generalFailCount"));
        boolean qtyOk = Boolean.TRUE.equals(report.get("quantityOk"));
        int n = asInt(report.get("sampleCount")) == null ? 0 : asInt(report.get("sampleCount"));
        BigDecimal y = InspectRules.yield(n, dc, dg);
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        applyInspectPlan(ws, d);
        AqlPlans.Plan plan = InspectPrices.includesAql(d == null ? null : d.getInspectMode())
                ? AqlPlans.plan(nvlDelivered(ws), d == null ? null : d.getAql()) : null;
        BigDecimal minY = asDecimal(report.get("minYield"));
        if (minY == null && d != null) {
            minY = d.getMinYield();
        }
        InspectRules.Branch branch = InspectRules.classify(qtyOk, dc, dg, y, minY, plan);
        int rework = ws.getReworkCount() == null ? 0 : ws.getReworkCount();
        ws.setBranchCode(branch.name());
        ws.setCanConcede(InspectRules.canConcede(branch, rework));
        ws.setCanRework(InspectRules.canRework(branch, rework));
        ws.setCanClose(true);
        ws.setMinYield(minY);
    }

    private static int nvlDelivered(WorkStage ws) {
        if (ws.getDeliveredQty() != null && ws.getDeliveredQty() > 0) {
            return ws.getDeliveredQty();
        }
        return ws.getQuantity() == null ? 0 : ws.getQuantity();
    }

    private void applyInspectPlan(WorkStage ws, Demand d) {
        if (d == null) {
            Order o = orderMapper.selectById(ws.getOrderId());
            d = o == null ? null : demandMapper.selectById(o.getDemandId());
        }
        if (d == null) {
            return;
        }
        ws.setInspectMode(d.getInspectMode());
        ws.setAql(d.getAql());
        ws.setMinYield(d.getMinYield());
        int agreed = ws.getQuantity() == null ? 0 : ws.getQuantity();
        int delivered = nvlDelivered(ws);
        ws.setRequiredSampleCount(InspectRules.requiredSample(d.getInspectMode(), d.getAql(), agreed, delivered));
        if (InspectPrices.includesAql(d.getInspectMode())) {
            AqlPlans.Plan plan = AqlPlans.plan(delivered, d.getAql());
            ws.setAqlAc(plan.ac());
            ws.setAqlRe(plan.re());
        }
    }

    private void fillInspectSheet(List<WorkStage> stages) {
        for (WorkStage ws : stages) {
            Order o = orderMapper.selectById(ws.getOrderId());
            Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
            if (d == null) {
                continue;
            }
            ws.setProductName(d.getProductName());
            ws.setCategory(d.getCategory());
            ws.setBuyerRemark(d.getRemark());
            ws.setTechSpecs(techSpecsOf(d));
            ws.setQualityThreshold(qualityThresholdOf(d, ws));
            applyInspectPlan(ws, d);
        }
    }

    private String techSpecsOf(Demand d) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "材料", d.getMaterial());
        addPart(parts, "关键公差", d.getTolerance());
        addPart(parts, "一般公差", d.getGeneralTolerance());
        addPart(parts, "表面处理", d.getSurfaceTreatment());
        addPart(parts, "图号/版本", d.getPartRevision());
        addPart(parts, "包装", d.getPackaging());
        Map<String, Object> extra = readExtra(d.getExtraJson());
        addPart(parts, "粗糙度", extra.get("roughness"));
        addPart(parts, "热处理", extra.get("heatTreatment"));
        return parts.isEmpty() ? "-" : String.join("；", parts);
    }

    private String qualityThresholdOf(Demand d, WorkStage ws) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "检验方式", InspectPrices.label(d.getInspectMode()));
        if (InspectPrices.includesAql(d.getInspectMode())) {
            int lot = nvlDelivered(ws);
            AqlPlans.Plan plan = AqlPlans.plan(lot, d.getAql());
            addPart(parts, "AQL", d.getAql());
            addPart(parts, "抽样方案", "水平II一次正常，实交 " + lot + " 抽 " + plan.n()
                    + "，一般缺陷 Ac=" + plan.ac() + " / Re=" + plan.re() + "；关键超差 0 件");
        } else {
            addPart(parts, "抽检规则", "全检实交件数");
            if (d.getMinYield() != null) {
                parts.add("最低良率 " + d.getMinYield());
            }
        }
        addPart(parts, "质检费", InspectPrices.label(d.getInspectMode()) + " " + InspectPrices.of(d.getInspectMode()) + " 元/件 × 本批实交");
        addPart(parts, "认证要求", d.getCertification());
        if (d.getMinCreditScore() != null) {
            parts.add("最低信用分 " + d.getMinCreditScore());
        }
        return parts.isEmpty() ? "-" : String.join("；", parts);
    }

    private static void addPart(List<String> parts, String label, Object value) {
        if (value == null) {
            return;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return;
        }
        parts.add(label + "：" + s);
    }

    private Map<String, Object> readExtra(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
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

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
