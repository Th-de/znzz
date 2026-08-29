package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.domain.coverage.ProcessCoverageService;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.dto.DemandDtos.*;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandService {

    private final DemandMapper demandMapper;
    private final ProcessMapper processMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final DemandStateMachine stateMachine;
    private final FundLedger fundLedger;
    private final ProcessCoverageService coverageService;
    private final QuotationMapper quotationMapper;
    private final DeviceService deviceService;
    private final AuditService auditService;
    private final EnterprisePublicService enterprisePublicService;

    @Transactional
    public Long publish(PublishRequest req) {
        validate(req);
        Demand d = new Demand();
        fill(d, req);
        d.setTenantId(UserContext.tenantId());
        d.setStatus(DemandStatus.DRAFT.name());
        d.setReturnReason("");
        d.setCancelReason("");
        d.setSourceDemandId(resolveSource(req.sourceDemandId()));
        stateMachine.transit(d, DemandStatus.PENDING_AUDIT);
        d.setIntentionEndAt(null);
        demandMapper.insert(d);
        replaceProcesses(d, req);
        if (req.attachmentId() != null) {
            fileService.bind(req.attachmentId(), "DEMAND", d.getId());
        }
        return d.getId();
    }

    @Transactional
    public Long republish(Long demandId, PublishRequest req) {
        Demand d = getOwned(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.RETURNED) {
            throw new BizException("仅退回修改的需求可以再次发布");
        }
        validate(req);
        fill(d, req);
        d.setReturnReason("");
        stateMachine.transit(d, DemandStatus.PENDING_AUDIT);
        demandMapper.updateById(d);
        demandMapper.update(null, new LambdaUpdateWrapper<Demand>()
                .eq(Demand::getId, d.getId())
                .set(Demand::getIntentionEndAt, null)
                .set(Demand::getPublishedAt, null)
                .set(Demand::getReturnReason, ""));
        replaceProcesses(d, req);
        if (req.attachmentId() != null) {
            fileService.bind(req.attachmentId(), "DEMAND", d.getId());
        }
        return d.getId();
    }

    @Transactional
    public void returnToBuyer(Long demandId, ReturnRequest req) {
        if (req == null || !StringUtils.hasText(req.reason())) {
            throw new BizException("退回必须填写原因");
        }
        Demand d = get(demandId);
        DemandStatus cur = DemandStatus.of(d.getStatus());
        if (cur != DemandStatus.PENDING_AUDIT && cur != DemandStatus.PUBLISHED) {
            throw new BizException("仅待审核或意向期的需求可以退回");
        }
        stateMachine.transit(d, DemandStatus.RETURNED);
        d.setReturnReason(req.reason().trim());
        demandMapper.updateById(d);
        demandMapper.update(null, new LambdaUpdateWrapper<Demand>()
                .eq(Demand::getId, d.getId())
                .set(Demand::getIntentionEndAt, null)
                .set(Demand::getPublishedAt, null));
        fundLedger.unfreezeIntentionsOfDemand(demandId);
        deviceService.releaseByDemand(demandId);
    }

    @Transactional
    public void cancelPublished(Long demandId, CancelRequest req) {
        if (req == null || !StringUtils.hasText(req.reason())) {
            throw new BizException("取消必须填写原因");
        }
        Demand d = getOwned(demandId);
        if (DemandStatus.of(d.getStatus()) != DemandStatus.PUBLISHED) {
            throw new BizException("只有已发布的需求可以取消后重新发布");
        }
        stateMachine.transit(d, DemandStatus.CANCELLED);
        d.setCancelReason(req.reason().trim());
        demandMapper.updateById(d);
        demandMapper.update(null, new LambdaUpdateWrapper<Demand>()
                .eq(Demand::getId, d.getId())
                .set(Demand::getIntentionEndAt, null));
        fundLedger.unfreezeIntentionsOfDemand(demandId);
        deviceService.releaseByDemand(demandId);
    }

    /** 兼容旧审核按钮：通过=发布，驳回=退回（无原因则拒绝）。 */
    @Transactional
    public void audit(Long demandId, AuditRequest req) {
        if (req == null || !StringUtils.hasText(req.result())) {
            throw new BizException("请选择审核结果");
        }
        if ("PASS".equals(req.result())) {
            Demand d = get(demandId);
            if (DemandStatus.of(d.getStatus()) == DemandStatus.PUBLISHED) {
                return;
            }
            stateMachine.transit(d, DemandStatus.PUBLISHED);
            d.setReturnReason("");
            d.setPublishedAt(LocalDateTime.now());
            d.setIntentionEndAt(intentionDeadline(d.getIntentionDays() == null ? 5 : d.getIntentionDays()));
            demandMapper.updateById(d);
            auditService.record("需求审核通过", "DEMAND", demandId, "「" + d.getTitle() + "」已发布");
            return;
        }
        auditService.record("需求退回", "DEMAND", demandId, req.reason());
        returnToBuyer(demandId, new ReturnRequest(req.reason()));
    }

    public DemandDetailView detail(Long id) {
        Demand d = get(id);
        String role = UserContext.role();
        boolean owner = UserContext.tenantId().equals(d.getTenantId());
        boolean staff = "OPERATOR".equals(role) || "SUPER_ADMIN".equals(role);
        boolean factoryOk = "FACTORY".equals(role)
                && (DemandStatus.PUBLISHED.name().equals(d.getStatus())
                || DemandStatus.FACTORY_THINKING.name().equals(d.getStatus())
                || DemandStatus.LOCKING.name().equals(d.getStatus()));
        if (!owner && !staff && !factoryOk) {
            throw new BizException(403, "无权查看该需求");
        }
        return new DemandDetailView(d, processes(id), fileService.listByBiz("DEMAND", id));
    }

    /** 买家/运营查看已报名工厂：名称、信誉、能力档案、设备状态。 */
    public List<Map<String, Object>> listBidFactories(Long demandId) {
        Demand d = get(demandId);
        String role = UserContext.role();
        boolean owner = UserContext.tenantId() != null && UserContext.tenantId().equals(d.getTenantId());
        boolean staff = "OPERATOR".equals(role) || "SUPER_ADMIN".equals(role);
        if (!owner && !staff) {
            throw new BizException(403, "无权查看报名工厂");
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "INTENTION", "LOCKED", "WIN"));
        Map<Long, List<Quotation>> byFactory = qs.stream()
                .filter(q -> q.getTenantId() != null)
                .collect(Collectors.groupingBy(Quotation::getTenantId, java.util.LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map.Entry<Long, List<Quotation>> e : byFactory.entrySet()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>(enterprisePublicService.publicProfile(e.getKey()));
            List<Integer> processNos = e.getValue().stream()
                    .map(Quotation::getProcessNo)
                    .filter(n -> n != null)
                    .distinct()
                    .toList();
            row.put("processNos", processNos);
            boolean committed = e.getValue().stream().anyMatch(q -> "LOCKED".equals(q.getStatus()) || "WIN".equals(q.getStatus()));
            row.put("bidStage", committed ? "QUOTED" : "INTENTION");
            out.add(row);
        }
        return out;
    }

    public CoverageView coverage(Long id) {
        detail(id);
        return coverageService.of(id);
    }

    public CancelStats cancelStats() {
        long n = demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, UserContext.tenantId())
                .eq(Demand::getStatus, DemandStatus.CANCELLED.name())
                .ge(Demand::getUpdatedAt, LocalDateTime.now().minusDays(90)));
        int count = (int) n;
        return new CancelStats(count, count >= 3);
    }

    public List<Demand> listMine() {
        return demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, UserContext.tenantId())
                .orderByDesc(Demand::getId));
    }

    public List<Demand> listPublished() {
        return demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DemandStatus.PUBLISHED.name())
                .orderByDesc(Demand::getId));
    }

    public List<Demand> listForFactory() {
        List<Demand> list = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .in(Demand::getStatus, DemandStatus.PUBLISHED.name(),
                        DemandStatus.FACTORY_THINKING.name(), DemandStatus.LOCKING.name())
                .orderByDesc(Demand::getId));
        attachFactoryApply(list);
        return list;
    }

    private void attachFactoryApply(List<Demand> list) {
        if (list.isEmpty() || UserContext.tenantId() == null) {
            return;
        }
        List<Long> ids = list.stream().map(Demand::getId).toList();
        List<Quotation> mine = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getTenantId, UserContext.tenantId())
                .in(Quotation::getDemandId, ids)
                .in(Quotation::getStatus, "INTENTION", "LOCKED"));
        Map<Long, List<Quotation>> byDemand = mine.stream().collect(Collectors.groupingBy(Quotation::getDemandId));
        for (Demand d : list) {
            List<Quotation> qs = byDemand.getOrDefault(d.getId(), List.of());
            d.setApplied(!qs.isEmpty());
            if (qs.stream().anyMatch(q -> "LOCKED".equals(q.getStatus()))) {
                d.setMyQuoteStatus("LOCKED");
            } else if (!qs.isEmpty()) {
                d.setMyQuoteStatus("INTENTION");
            }
        }
    }

    public List<Demand> listAll() {
        return demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .orderByDesc(Demand::getId));
    }

    public Demand get(Long id) {
        Demand d = demandMapper.selectById(id);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        return d;
    }

    public List<Process> processes(Long demandId) {
        return processMapper.selectList(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, demandId)
                .orderByAsc(Process::getProcessNo));
    }

    private Long resolveSource(Long sourceDemandId) {
        if (sourceDemandId == null) {
            return null;
        }
        Demand src = getOwned(sourceDemandId);
        if (DemandStatus.of(src.getStatus()) != DemandStatus.CANCELLED) {
            throw new BizException("只能基于已取消的需求重新发布");
        }
        return src.getId();
    }

    private Demand getOwned(Long id) {
        Demand d = get(id);
        if (!UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "无权操作该需求");
        }
        return d;
    }

    private void fill(Demand d, PublishRequest req) {
        d.setTitle(req.title().trim());
        d.setProductName(req.productName().trim());
        d.setCategory(req.category());
        d.setQuantity(req.quantity());
        d.setMaterial(req.material().trim());
        d.setTolerance(req.tolerance());
        d.setSurfaceTreatment(req.surfaceTreatment());
        d.setAql(req.aql());
        d.setCertification(req.certification());
        d.setMinYield(req.minYield());
        d.setMinCreditScore(req.minCreditScore());
        d.setDeadlineHard(req.deadlineHard());
        d.setDeadlineFlexible(req.deadlineFlexible());
        d.setDeliveryAddress(req.deliveryAddress().trim());
        d.setPackaging(req.packaging());
        d.setMultiProcess(1);
        d.setIntentionDays(req.intentionDays() == null ? 5 : req.intentionDays());
        d.setRemark(req.remark());
        d.setInspectMode(req.inspectMode());
        d.setGeneralTolerance(req.generalTolerance());
        d.setPartRevision(req.partRevision());
        d.setExtraJson(req.extraJson());
        d.setDeliveryTimes(req.deliveryTimes() == null || req.deliveryTimes() <= 0 ? 1 : req.deliveryTimes());
        try {
            d.setDeliveryPlanJson(objectMapper.writeValueAsString(
                    req.deliveryPlan() == null ? List.of() : req.deliveryPlan()));
        } catch (Exception e) {
            d.setDeliveryPlanJson("[]");
        }
    }

    private void replaceProcesses(Demand d, PublishRequest req) {
        processMapper.delete(new LambdaQueryWrapper<Process>().eq(Process::getDemandId, d.getId()));
        boolean hasProcess = req.processes() != null && req.processes().stream()
                .anyMatch(p -> p != null && StringUtils.hasText(p.processName()));
        if (d.getMultiProcess() != null && d.getMultiProcess() == 1 && hasProcess) {
            int i = 1;
            for (ProcessItem item : req.processes()) {
                if (item == null || !StringUtils.hasText(item.processName())) {
                    throw new BizException("工序名称不能为空");
                }
                Process p = new Process();
                p.setDemandId(d.getId());
                p.setProcessNo(item.processNo() == null ? i : item.processNo());
                p.setProcessName(item.processName().trim());
                if (item.quantity() == null || item.quantity() <= 0) {
                    throw new BizException("工序「" + item.processName().trim() + "」必须填写本工序数量，不会默认成总需求量");
                }
                p.setQuantity(item.quantity());
                p.setRequirement(item.requirement());
                processMapper.insert(p);
                i++;
            }
            return;
        }
        Process p = new Process();
        p.setDemandId(d.getId());
        p.setProcessNo(1);
        p.setProcessName("整单");
        p.setQuantity(d.getQuantity());
        p.setRequirement("");
        processMapper.insert(p);
    }

    private LocalDateTime intentionDeadline(int days) {
        int n = Math.max(days, 1);
        return LocalDateTime.now().plusDays(n);
    }

    private void validate(PublishRequest req) {
        if (!StringUtils.hasText(req.title()) || !StringUtils.hasText(req.productName())) {
            throw new BizException("请填写需求标题和产品名称");
        }
        if (req.quantity() == null || req.quantity() <= 0) {
            throw new BizException("数量必须大于 0");
        }
        if (!StringUtils.hasText(req.material())) {
            throw new BizException("请填写材料牌号");
        }
        if (!StringUtils.hasText(req.aql())) {
            throw new BizException("请选择 AQL");
        }
        if (req.minYield() == null) {
            throw new BizException("请填写最低良率");
        }
        if (req.deadlineHard() == null || !req.deadlineHard().isAfter(LocalDate.now())) {
            throw new BizException("硬交期必须晚于今天");
        }
        if (!StringUtils.hasText(req.deliveryAddress())) {
            throw new BizException("请填写交付地址");
        }
        if (req.attachmentId() == null) {
            throw new BizException("请上传图纸或需求文档");
        }
        if (!StringUtils.hasText(req.category())) {
            throw new BizException("请选择产品类别");
        }
        if (!StringUtils.hasText(req.partRevision())) {
            throw new BizException("请填写图号/版本");
        }
        if (!StringUtils.hasText(req.generalTolerance())) {
            throw new BizException("请选择一般公差");
        }
        if (!StringUtils.hasText(req.tolerance())) {
            throw new BizException("请填写关键公差");
        }
        if (!StringUtils.hasText(req.surfaceTreatment())) {
            throw new BizException("请填写表面处理");
        }
        if (!StringUtils.hasText(req.inspectMode())) {
            throw new BizException("请选择检验方式");
        }
        if (!StringUtils.hasText(req.certification())) {
            throw new BizException("请选择认证要求");
        }
        if (!StringUtils.hasText(req.packaging())) {
            throw new BizException("请填写包装要求");
        }
        JsonNode extra = parseExtra(req.extraJson());
        if (!StringUtils.hasText(extra.path("roughness").asText(null))) {
            throw new BizException("请选择粗糙度");
        }
        if (!StringUtils.hasText(extra.path("heatTreatment").asText(null))) {
            throw new BizException("请填写热处理");
        }
        List<ProcessItem> namedItems = req.processes() == null ? List.of() : req.processes().stream()
                .filter(p -> p != null && StringUtils.hasText(p.processName()))
                .toList();
        if (namedItems.isEmpty()) {
            throw new BizException("请至少填写 1 道工序");
        }
        int processSum = namedItems.stream()
                .mapToInt(p -> p.quantity() == null ? 0 : p.quantity())
                .sum();
        if (req.quantity() == null || processSum != req.quantity()) {
            throw new BizException("各工序零件数量之和必须等于总需求量（" + req.quantity() + " 件）");
        }
        int times = req.deliveryTimes() == null ? 1 : req.deliveryTimes();
        if (times < 1 || times > 10) {
            throw new BizException("分期交付次数须在 1~10 之间");
        }
        if (times > 1) {
            if (req.deliveryPlan() == null || req.deliveryPlan().size() != times
                    || req.deliveryPlan().stream().anyMatch(s -> !StringUtils.hasText(s))) {
                throw new BizException("请填写每一期的交付要求（共 " + times + " 期）");
            }
        }
    }

    private JsonNode parseExtra(String extraJson) {
        try {
            if (!StringUtils.hasText(extraJson)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(extraJson);
        } catch (Exception e) {
            throw new BizException("技术扩展字段格式不正确");
        }
    }

}
