package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.pay.PaymentChannel;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.domain.inspect.AqlPlans;
import com.dsh.platform.domain.inspect.InspectPrices;
import com.dsh.platform.domain.inspect.InspectRules;
import com.dsh.platform.dto.OrderDtos.DecisionRequest;
import com.dsh.platform.dto.OrderDtos.InspectRequest;
import com.dsh.platform.dto.OrderDtos.ProgressRequest;
import com.dsh.platform.dto.OrderDtos.SurveyRequest;
import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.StageProgressLog;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.AttachmentMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.InspectionMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.mapper.SurveyMapper;
import com.dsh.platform.mapper.StageProgressLogMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final DemandMapper demandMapper;
    private final SolutionMapper solutionMapper;
    private final OrderMapper orderMapper;
    private final WorkStageMapper workStageMapper;
    private final InspectionMapper inspectionMapper;
    private final QuotationMapper quotationMapper;
    private final SurveyMapper surveyMapper;
    private final ObjectMapper objectMapper;
    private final DemandStateMachine stateMachine;
    private final ContractService contractService;
    private final PaymentChannel paymentChannel;
    private final CreditScoring creditScoring;
    private final SiteNotify siteNotify;
    private final StageProgressLogMapper stageProgressLogMapper;
    private final FileService fileService;
    private final AttachmentMapper attachmentMapper;
    private final FundLedger fundLedger;
    private final AuditService auditService;
    private final com.dsh.platform.mapper.ContractMapper contractMapper;

    @Value("${dsh.fee.commission-rate}")
    private BigDecimal commissionRate;
    @Value("${dsh.time.contract-issue-hours:48}")
    private int contractIssueHours;
    @Value("${dsh.time.contract-sign-hours:24}")
    private int contractSignHours;

    @Transactional
    public void confirmSolution(Long demandId, Long solutionId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("当前状态不可确认方案");
        }
        if (!UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能选自己的需求方案");
        }
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null || !demandId.equals(s.getDemandId())) {
            throw new BizException("方案不存在");
        }
        if (!"ACTIVE".equals(s.getStatus())) {
            throw new BizException("该方案尚未由运营下发");
        }
        s.setIsFinal(1);
        s.setSource("FINAL");
        s.setEditedBy(UserContext.userId());
        solutionMapper.updateById(s);
        stateMachine.transit(d, DemandStatus.SOLUTION_CONFIRMED);
        demandMapper.updateById(d);
        siteNotify.send(d.getTenantId(), "方案已确认#" + demandId,
                "需求「" + d.getTitle() + "」方案已确认，等待运营派单。");
        siteNotify.notifyOperators("买家已确认方案#" + demandId,
                "买家已确认需求「" + d.getTitle() + "」的方案，请到需求管理派单。");
    }

    @Transactional
    public Long dispatch(Long demandId) {
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以派单");
        }
        Demand d = demandMapper.selectById(demandId);
        if (d == null || !"SOLUTION_CONFIRMED".equals(d.getStatus())) {
            throw new BizException("仅买家确认后的方案可以派单");
        }
        Solution s = solutionMapper.selectOne(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId)
                .eq(Solution::getIsFinal, 1)
                .last("limit 1"));
        if (s == null) {
            throw new BizException("没有买家确认的方案");
        }
        List<Map<String, Object>> combo = readCombo(s.getFinalComboJson());
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> c : combo) {
            total = total.add(new BigDecimal(c.get("price").toString()));
        }
        Order o = new Order();
        o.setDemandId(demandId);
        o.setSolutionId(s.getId());
        o.setTotalAmount(total);
        o.setCommissionRate(commissionRate);
        o.setCommissionAmount(total.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP));
        o.setStatus("CREATED");
        orderMapper.insert(o);
        o.setContractIssueEndAt(LocalDateTime.now().plusHours(Math.max(contractIssueHours, 1)));
        orderMapper.updateById(o);
        contractService.createDrafts(o.getId(), s.getFinalComboJson());
        // 同工序可多厂分量：按 (厂, 工序) 组合判定 WIN/LOSE，落选逐一退款
        java.util.Set<String> winPairs = new java.util.HashSet<>();
        Map<Long, java.util.List<String>> winProcessNames = new LinkedHashMap<>();
        for (Map<String, Object> c : combo) {
            Long fid = Long.valueOf(c.get("factoryId").toString());
            Integer pno = Integer.valueOf(c.get("processNo").toString());
            winPairs.add(fid + "-" + pno);
            String pname = c.get("processName") == null ? ("工序" + pno) : c.get("processName").toString();
            Integer qty = c.get("quantity") == null ? null
                    : new BigDecimal(c.get("quantity").toString()).intValue();
            winProcessNames.computeIfAbsent(fid, k -> new java.util.ArrayList<>())
                    .add(pname + (qty == null ? "" : "×" + qty + "件"));
        }
        List<Quotation> allQuotes = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "LOCKED", "INTENTION"));
        for (Quotation q : allQuotes) {
            Integer pno = q.getProcessNo() == null ? 1 : q.getProcessNo();
            boolean win = winPairs.contains(q.getTenantId() + "-" + pno);
            q.setStatus(win ? "WIN" : "LOSE");
            quotationMapper.updateById(q);
            if (!win) {
                fundLedger.refundLoser(q);
            }
        }
        List<Long> winFactoryIds = new java.util.ArrayList<>(winProcessNames.keySet());
        stateMachine.transit(d, DemandStatus.SOLUTION_SELECTED);
        demandMapper.updateById(d);
        siteNotify.send(d.getTenantId(), "请按厂上传合同#" + o.getId(), "方案已派单。请按中标厂分别上传你们拟定的合同并签名。");
        // 只把各厂自己承接的那部分发给它，不发整体方案
        winProcessNames.forEach((fid, names) -> siteNotify.send(fid, "请签合同#" + o.getId(),
                "你已中标，承接内容：" + String.join("、", names)
                        + "。买家上传与你的合同后，请阅读并签名。"));
        auditService.record("派单", "DEMAND", demandId,
                "订单#" + o.getId() + " 总额" + total + " 中标厂" + winFactoryIds);
        return o.getId();
    }

    @Transactional
    public void startStage(Long stageId) {
        WorkStage ws = requireFactoryStage(stageId);
        if ("WAITING_OPEN".equals(ws.getStatus())) {
            activateIfWindowOpen(ws);
            if ("IN_PRODUCTION".equals(ws.getStatus())) {
                return;
            }
            throw new BizException("尚未到本期开始时间，不能开工");
        }
        if (!"PENDING".equals(ws.getStatus())) {
            throw new BizException("只有待启动的工单可以开工");
        }
        if (!contractService.isSigned(ws.getOrderId(), ws.getTenantId())) {
            throw new BizException("与你的合同未双签或平台未审过，不能开工");
        }
        if (ws.getPeriodStart() != null && LocalDateTime.now().isBefore(ws.getPeriodStart())) {
            ws.setStatus("WAITING_OPEN");
            workStageMapper.updateById(ws);
            throw new BizException("尚未到本期开始时间");
        }
        ws.setStatus("IN_PRODUCTION");
        workStageMapper.updateById(ws);
    }

    @Transactional
    public void reportProgress(Long stageId, ProgressRequest req) {
        WorkStage ws = requireFactoryStage(stageId);
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            activateIfWindowOpen(ws);
        }
        if ("WAITING_OPEN".equals(ws.getStatus())) {
            throw new BizException("尚未到本期开始时间，不能上报进度");
        }
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            throw new BizException("本期未开启，不能上报进度");
        }
        if (req == null || req.doneQty() == null || req.doneQty() <= 0) {
            throw new BizException("请填写完成件数");
        }
        if (req.remark() == null || req.remark().isBlank()) {
            throw new BizException("请填写进度说明");
        }
        int qty = ws.getQuantity() == null ? 0 : ws.getQuantity();
        if (req.doneQty() > qty) {
            throw new BizException("完成件数不能超过本段数量 " + qty);
        }
        StageProgressLog last = stageProgressLogMapper.selectOne(new LambdaQueryWrapper<StageProgressLog>()
                .eq(StageProgressLog::getStageId, stageId)
                .orderByDesc(StageProgressLog::getId)
                .last("limit 1"));
        int prev = last == null || last.getDoneQty() == null ? 0 : last.getDoneQty();
        if (req.doneQty() <= prev) {
            throw new BizException("完成件数必须大于上次上报的 " + prev);
        }
        int progress = qty <= 0 ? 100 : Math.min(100, req.doneQty() * 100 / qty);
        ws.setActualProgress(progress);
        ws.setDeliveredQty(req.doneQty());
        ws.setUpdatedAt(java.time.LocalDateTime.now());
        workStageMapper.updateById(ws);
        StageProgressLog log = new StageProgressLog();
        log.setStageId(stageId);
        log.setDoneQty(req.doneQty());
        log.setProgress(progress);
        log.setRemark(req.remark().trim());
        log.setAttachmentId(req.attachmentId());
        stageProgressLogMapper.insert(log);
        if (req.attachmentId() != null) {
            fileService.bind(req.attachmentId(), "PROGRESS", stageId);
        }
    }

    public List<StageProgressLog> progressLog(Long stageId) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        assertCanViewStage(ws);
        List<StageProgressLog> list = stageProgressLogMapper.selectList(new LambdaQueryWrapper<StageProgressLog>()
                .eq(StageProgressLog::getStageId, stageId)
                .orderByDesc(StageProgressLog::getId));
        for (StageProgressLog log : list) {
            if (log.getAttachmentId() != null) {
                Attachment a = attachmentMapper.selectById(log.getAttachmentId());
                log.setFileName(a == null ? null : a.getFileName());
            }
        }
        return list;
    }

    public Inspection inspectionOf(Long stageId) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        assertCanViewStage(ws);
        return inspectionMapper.selectOne(new LambdaQueryWrapper<Inspection>()
                .eq(Inspection::getStageId, stageId)
                .orderByDesc(Inspection::getId)
                .last("limit 1"));
    }

    @Transactional
    public void deliver(Long stageId, Integer deliveredQty) {
        WorkStage ws = requireFactoryStage(stageId);
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            activateIfWindowOpen(ws);
        }
        if ("WAITING_OPEN".equals(ws.getStatus())) {
            throw new BizException("尚未到本期开始时间，不能交付");
        }
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            throw new BizException("本期未开启，不能交付");
        }
        if (!contractService.isSigned(ws.getOrderId(), ws.getTenantId())) {
            throw new BizException("与你的合同未双签或平台未审过，不能交付");
        }
        int agreed = ws.getQuantity() == null ? 0 : ws.getQuantity();
        int delivered = deliveredQty != null ? deliveredQty : nvlInt(ws.getDeliveredQty());
        if (delivered <= 0) {
            StageProgressLog last = stageProgressLogMapper.selectOne(new LambdaQueryWrapper<StageProgressLog>()
                    .eq(StageProgressLog::getStageId, stageId)
                    .orderByDesc(StageProgressLog::getId)
                    .last("limit 1"));
            delivered = last == null || last.getDoneQty() == null ? 0 : last.getDoneQty();
        }
        if (delivered <= 0) {
            throw new BizException("请填写实交件数");
        }
        if (delivered > agreed && agreed > 0) {
            throw new BizException("实交件数不能超过约定数量 " + agreed);
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        ws.setDeliveredQty(delivered);
        ws.setActualProgress(agreed <= 0 ? 100 : Math.min(100, delivered * 100 / agreed));
        ws.setUpdatedAt(java.time.LocalDateTime.now());
        enterInspectPay(ws, o, d);
        creditScoring.onDeliver(ws);
    }

    private void activateIfWindowOpen(WorkStage ws) {
        if (ws.getPeriodStart() != null && LocalDateTime.now().isBefore(ws.getPeriodStart())) {
            return;
        }
        if (!contractService.isSigned(ws.getOrderId(), ws.getTenantId())) {
            return;
        }
        if (!"WAITING_OPEN".equals(ws.getStatus()) && !"PENDING".equals(ws.getStatus())) {
            return;
        }
        ws.setStatus("IN_PRODUCTION");
        ws.setUpdatedAt(LocalDateTime.now());
        workStageMapper.updateById(ws);
    }

    private WorkStage requireFactoryStage(Long stageId) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || !ws.getTenantId().equals(UserContext.tenantId())) {
            throw new BizException("工单不存在或无权操作");
        }
        return ws;
    }

    private void assertCanViewStage(WorkStage ws) {
        String role = UserContext.role();
        if ("OPERATOR".equals(role) || "SUPER_ADMIN".equals(role) || "INSPECTION".equals(role)) {
            return;
        }
        if ("FACTORY".equals(role) && UserContext.tenantId().equals(ws.getTenantId())) {
            return;
        }
        if ("BUYER".equals(role)) {
            Order o = orderMapper.selectById(ws.getOrderId());
            Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
            if (d != null && UserContext.tenantId().equals(d.getTenantId())) {
                return;
            }
        }
        throw new BizException(403, "无权查看该工单");
    }

    @Transactional
    public void inspect(Long stageId, InspectRequest req) {
        if (req == null) {
            throw new BizException("请填写质检单");
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || !"PENDING_INSPECTION".equals(ws.getStatus())) {
            throw new BizException("工单状态不可质检");
        }
        if (!"INSPECTION".equals(UserContext.role())) {
            throw new BizException(403, "请由质检方填写质检单");
        }
        int agreed = ws.getQuantity() == null ? 0 : ws.getQuantity();
        int delivered = nvlInt(ws.getDeliveredQty());
        if (delivered <= 0) {
            throw new BizException("工厂尚未登记实交件数，不能质检");
        }
        boolean qtyOk = delivered >= agreed;
        int dc = nvlInt(req.criticalFailCount());
        int dg = nvlInt(req.generalFailCount());
        int n = nvlInt(req.sampleCount());
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        String mode = d == null ? null : d.getInspectMode();
        AqlPlans.Plan aqlPlan = InspectPrices.includesAql(mode) ? AqlPlans.plan(delivered, d.getAql()) : null;
        int need = InspectRules.requiredSample(mode, d == null ? null : d.getAql(), agreed, delivered);
        if (n < need) {
            throw new BizException("抽检数不能少于规定的 " + need + " 件");
        }
        if (dc + dg > n) {
            throw new BizException("不合格件数不能超过抽检数");
        }
        BigDecimal minY = aqlPlan != null ? null : (d == null || d.getMinYield() == null ? BigDecimal.ZERO : d.getMinYield());
        BigDecimal y = InspectRules.yield(n, dc, dg);
        boolean toleranceOk = InspectRules.tolerancePass(mode, dc, dg, y, minY, aqlPlan);
        boolean pass = InspectRules.isPass(qtyOk, toleranceOk);
        String result = pass ? "PASS" : "FAIL";
        if (req.result() != null && !result.equals(req.result())) {
            throw new BizException("是否合格须与检验数据一致：数量达标且公差合格才为合格");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sampleCount", n);
        report.put("failCount", dc + dg);
        report.put("criticalFailCount", dc);
        report.put("generalFailCount", dg);
        report.put("deliveredQty", delivered);
        report.put("keyDimensions", req.keyDimensions() == null ? "" : req.keyDimensions());
        report.put("meetsRequirement", pass);
        report.put("remark", req.remark() == null ? "" : req.remark());
        report.put("actualYield", y);
        report.put("quantityOk", qtyOk);
        report.put("toleranceOk", toleranceOk);
        if (minY != null) {
            report.put("minYield", minY);
        }
        if (aqlPlan != null) {
            report.put("aql", d.getAql());
            report.put("aqlAc", aqlPlan.ac());
            report.put("aqlRe", aqlPlan.re());
        }
        report.put("inspectMode", mode);
        String reportJson = writeJson(report);

        Inspection ins = new Inspection();
        ins.setStageId(stageId);
        ins.setInspectorTenantId(UserContext.tenantId());
        ins.setResult(result);
        ins.setReportJson(reportJson);
        ins.setHash(sha256(result + reportJson));
        ins.setStatus("VALID");
        inspectionMapper.insert(ins);

        ws.setStatus("PENDING_REVIEW");
        ws.setUpdatedAt(LocalDateTime.now());
        workStageMapper.updateById(ws);
        siteNotify.notifyOperators("待审核质检单#" + stageId,
                "工单「" + ws.getProcessName() + "」质检方已提交，请审核。");
        siteNotify.send(ws.getTenantId(), "质检已提交#" + stageId, "质检单已提交，等待平台审核。");
        auditService.record("提交质检单", "STAGE", stageId, "工序「" + ws.getProcessName() + "」待运营审核");
    }

    @Transactional
    public void approveInspect(Long stageId, InspectRequest req) {
        String role = UserContext.role();
        if (!"OPERATOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BizException(403, "仅运营可审核质检单");
        }
        if (req == null || req.result() == null) {
            throw new BizException("请选择是否合格");
        }
        String result = req.result();
        if (!"PASS".equals(result) && !"FAIL".equals(result)) {
            throw new BizException("质检结果只能是合格或不合格");
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || !"PENDING_REVIEW".equals(ws.getStatus())) {
            throw new BizException("请等待质检方提交后再审核");
        }
        Inspection ins = inspectionMapper.selectOne(new LambdaQueryWrapper<Inspection>()
                .eq(Inspection::getStageId, stageId)
                .orderByDesc(Inspection::getId)
                .last("limit 1"));
        if (ins == null) {
            throw new BizException("质检方尚未提交质检单");
        }
        Map<String, Object> report = readReport(ins.getReportJson());
        int dc = mapInt(report, "criticalFailCount");
        int dg = mapInt(report, "generalFailCount");
        int n = mapInt(report, "sampleCount");
        boolean qtyOk = Boolean.TRUE.equals(report.get("quantityOk"));
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        int delivered = nvlInt(ws.getDeliveredQty());
        String mode = d == null ? null : d.getInspectMode();
        AqlPlans.Plan aqlPlan = InspectPrices.includesAql(mode) ? AqlPlans.plan(delivered, d.getAql()) : null;
        BigDecimal minY = aqlPlan != null ? null : (d == null || d.getMinYield() == null ? BigDecimal.ZERO : d.getMinYield());
        BigDecimal y = InspectRules.yield(n, dc, dg);
        boolean toleranceOk = InspectRules.tolerancePass(mode, dc, dg, y, minY, aqlPlan);
        boolean pass = InspectRules.isPass(qtyOk, toleranceOk);
        result = pass ? "PASS" : "FAIL";
        if (req.result() != null && !result.equals(req.result())) {
            throw new BizException("是否合格须与检验数据一致：数量达标且公差合格才为合格");
        }
        ins.setResult(result);
        report.put("meetsRequirement", pass);
        report.put("toleranceOk", toleranceOk);
        if (minY != null) {
            report.put("minYield", minY);
        }
        String reportJson = writeJson(report);
        ins.setReportJson(reportJson);
        ins.setHash(sha256(result + reportJson));
        inspectionMapper.updateById(ins);

        rememberFirstInspect(ws, report);
        int round = nvlInt(ws.getInspectRound()) + 1;
        ws.setInspectRound(round);
        ws.setUpdatedAt(LocalDateTime.now());
        if ("PASS".equals(result)) {
            ws.setPayAmount(payableOnPass(ws, report));
            ws.setStatus("PASS");
            workStageMapper.updateById(ws);
            if (d != null) {
                siteNotify.send(d.getTenantId(), "请付阶段款#" + stageId,
                        "工单「" + ws.getProcessName() + "」已审核可收款，请支付本阶段托管 ¥" + ws.getPayAmount() + "。");
            }
            if (d != null) {
                siteNotify.send(d.getTenantId(), "阶段问卷#" + stageId, "请对本阶段做总体星级评价。");
            }
            siteNotify.send(ws.getTenantId(), "阶段问卷#" + stageId, "请对本阶段做总体星级评价。");
            creditScoring.onInspectResult(ws, true, ins.getId());
        } else {
            ws.setStatus("FAIL");
            workStageMapper.updateById(ws);
            if (d != null) {
                siteNotify.send(d.getTenantId(), "请处理质检结果#" + stageId,
                        "工单「" + ws.getProcessName() + "」审核为不合格。请按规则选择让步、返工或关闭本段。");
            }
            siteNotify.send(ws.getTenantId(), "质检不合格#" + stageId, "本段审核不合格，等待买家选择让步、返工或关闭。");
            creditScoring.onInspectResult(ws, false, ins.getId());
        }
        auditService.record("审核质检单", "STAGE", stageId,
                "结果" + result + " 工序「" + ws.getProcessName() + "」");
    }

    @Transactional
    public void decideInspect(Long stageId, DecisionRequest req) {
        if (req == null || req.action() == null) {
            throw new BizException("请选择让步、返工或关闭");
        }
        String action = req.action().trim().toUpperCase();
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || !"FAIL".equals(ws.getStatus())) {
            throw new BizException("仅不合格待处理的工单可由买家决策");
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能处理自己订单的工单");
        }
        Inspection ins = latestInspection(stageId);
        Map<String, Object> report = ins == null ? Map.of() : readReport(ins.getReportJson());
        int dc = mapInt(report, "criticalFailCount");
        int dg = mapInt(report, "generalFailCount");
        boolean qtyOk = Boolean.TRUE.equals(report.get("quantityOk"));
        int rework = nvlInt(ws.getReworkCount());
        BigDecimal A = stageAmount(ws);
        BigDecimal y = InspectRules.yield(
                mapInt(report, "sampleCount"), dc, dg);
        int delivered = nvlInt(ws.getDeliveredQty());
        int agreed = ws.getQuantity() == null ? 0 : ws.getQuantity();
        AqlPlans.Plan aqlPlan = InspectPrices.includesAql(d.getInspectMode())
                ? AqlPlans.plan(delivered, d.getAql()) : null;
        InspectRules.Branch branch = InspectRules.classify(qtyOk, dc, dg, y, d.getMinYield(), aqlPlan);

        if ("CONCESSION".equals(action)) {
            if (!InspectRules.canConcede(branch, rework)) {
                throw new BizException("当前质检结论不能让步");
            }
            BigDecimal pay = InspectRules.concessionPay(branch, A, agreed, delivered, y);
            BigDecimal penalty = InspectRules.concessionPenalty(branch, A, d.getMinYield(), y, dg, aqlPlan);
            BigDecimal paid = fundLedger.payConcessionToBuyer(
                    o.getId(), d.getId(), ws.getTenantId(), d.getTenantId(), stageId, penalty);
            ws.setPayAmount(pay);
            ws.setStatus("PASS");
            ws.setUpdatedAt(LocalDateTime.now());
            workStageMapper.updateById(ws);
            String msg;
            if (branch == InspectRules.Branch.B) {
                msg = "已交部分工费已托管，并按本阶段约定工费 5% 从工厂保证金赔付买家。";
            } else if (aqlPlan != null) {
                msg = "本段工费全额托管，并按本阶段约定工费 5% 从工厂保证金赔付买家。";
            } else {
                msg = "本段工费全额托管，保证金已按（最低良率−实际良率）赔付买家。";
            }
            siteNotify.send(d.getTenantId(), "让步已确认#" + stageId,
                    msg + " 托管 ¥" + pay + "，赔付 ¥" + paid + "。请支付托管款。");
            siteNotify.send(ws.getTenantId(), "买家已让步#" + stageId,
                    "买家让步接收。本段托管 ¥" + pay + "，保证金已赔付 ¥" + paid + "。");
            siteNotify.send(d.getTenantId(), "阶段问卷#" + stageId, "请对本阶段做总体星级评价。");
            siteNotify.send(ws.getTenantId(), "阶段问卷#" + stageId, "请对本阶段做总体星级评价。");
            creditScoring.onConcessionPass(ws);
            auditService.record("让步接收", "STAGE", stageId, branch + " 托管" + pay + " 赔付" + paid);
            return;
        }
        if ("REWORK".equals(action)) {
            if (!InspectRules.canRework(branch, rework)) {
                throw new BizException("不能再返工，请让步或关闭");
            }
            int hours = req.reworkHours() == null ? 24 : req.reworkHours();
            if (hours < 12 || hours > 72) {
                throw new BizException("返工期限须为 12～72 小时");
            }
            if (branch == InspectRules.Branch.B) {
                splitQtyRework(ws, o, d, report, hours, A, agreed, delivered);
                return;
            }
            ws.setReworkCount(1);
            ws.setReworkKind("QUALITY");
            ws.setReworkDeadlineAt(LocalDateTime.now().plusHours(hours));
            ws.setReworkReason(buildReworkReason(report));
            ws.setStatus("IN_PRODUCTION");
            ws.setActualProgress(0);
            ws.setInspectFeeStatus("NONE");
            ws.setUpdatedAt(LocalDateTime.now());
            workStageMapper.updateById(ws);
            siteNotify.send(ws.getTenantId(), "请返工#" + stageId,
                    "买家要求质量返工，期限 " + hours + " 小时。再次交付后由工厂支付质检费。");
            siteNotify.send(d.getTenantId(), "已要求返工#" + stageId,
                    "已通知工厂质量返工，期限 " + hours + " 小时。");
            auditService.record("要求返工", "STAGE", stageId, "QUALITY " + hours + "小时");
            return;
        }
        if ("CLOSE".equals(action)) {
            closeChain(ws, o, d);
            return;
        }
        throw new BizException("请选择让步、返工或关闭");
    }

    private void enterInspectPay(WorkStage ws, Order o, Demand d) {
        boolean qualityRework = nvlInt(ws.getReworkCount()) > 0 && "QUALITY".equals(ws.getReworkKind());
        String payer = qualityRework ? "FACTORY" : "BUYER";
        int feeQty = qualityRework
                ? (ws.getQuantity() == null ? 0 : ws.getQuantity())
                : nvlInt(ws.getDeliveredQty());
        BigDecimal fee = InspectPrices.fee(d == null ? null : d.getInspectMode(), feeQty);
        ws.setInspectFeePayer(payer);
        ws.setInspectFeeAmount(fee);
        if (fee.compareTo(BigDecimal.ZERO) <= 0) {
            ws.setInspectFeeStatus("PAID");
            ws.setStatus("PENDING_INSPECTION");
            workStageMapper.updateById(ws);
            return;
        }
        ws.setInspectFeeStatus("PENDING_PAY");
        ws.setStatus("PENDING_INSPECT_PAY");
        workStageMapper.updateById(ws);
        Long demandId = d == null ? null : d.getId();
        if ("FACTORY".equals(payer)) {
            siteNotify.send(ws.getTenantId(), "请付质检费#" + ws.getId(),
                    "质量返工须先缴质检费 ¥" + fee + "，付完后质检员才能检验。");
        } else if (demandId != null) {
            siteNotify.send(d.getTenantId(), "请付质检费#" + ws.getId(),
                    "工厂已交付，请先支付质检费 ¥" + fee + "，付完后进入质检。");
        }
    }

    @Transactional
    public com.dsh.platform.domain.pay.EscrowStart payInspectFee(Long stageId) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        if (!"PENDING_INSPECT_PAY".equals(ws.getStatus()) && !"PENDING_PAY".equals(ws.getInspectFeeStatus())) {
            throw new BizException("当前无需支付质检费");
        }
        if ("PAID".equals(ws.getInspectFeeStatus())) {
            return new com.dsh.platform.domain.pay.EscrowStart(true, null);
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        String payer = ws.getInspectFeePayer() == null ? "BUYER" : ws.getInspectFeePayer();
        Long payerId = "FACTORY".equals(payer) ? ws.getTenantId() : (d == null ? null : d.getTenantId());
        if (payerId == null || !payerId.equals(UserContext.tenantId())) {
            throw new BizException(403, "请由应付质检费的一方支付");
        }
        BigDecimal fee = ws.getInspectFeeAmount() == null ? BigDecimal.ZERO : ws.getInspectFeeAmount();
        if (o == null || d == null) {
            throw new BizException("订单不存在");
        }
        fundLedger.collectInspectFee(o.getId(), d.getId(), payerId, ws.getId(), fee);
        ws.setInspectFeeStatus("PAID");
        ws.setStatus("PENDING_INSPECTION");
        ws.setUpdatedAt(LocalDateTime.now());
        workStageMapper.updateById(ws);
        auditService.record("支付质检费", "STAGE", ws.getId(), payer + " " + fee);
        return new com.dsh.platform.domain.pay.EscrowStart(true, null);
    }

    private void splitQtyRework(WorkStage ws, Order o, Demand d, Map<String, Object> report,
                                int hours, BigDecimal A, int agreed, int delivered) {
        int remain = Math.max(0, agreed - delivered);
        BigDecimal deliveredPay = InspectRules.concessionPay(InspectRules.Branch.B, A, agreed, delivered, BigDecimal.ONE);
        ws.setPayAmount(deliveredPay);
        ws.setStatus("PASS");
        ws.setReworkCount(1);
        ws.setReworkKind("QTY");
        ws.setReworkReason(buildReworkReason(report));
        ws.setUpdatedAt(LocalDateTime.now());
        workStageMapper.updateById(ws);
        if (remain > 0) {
            WorkStage child = new WorkStage();
            child.setOrderId(ws.getOrderId());
            child.setTenantId(ws.getTenantId());
            child.setProcessNo(ws.getProcessNo());
            child.setProcessName((ws.getPeriodLabel() == null ? ws.getProcessName() : ws.getPeriodLabel()) + " 补件");
            child.setQuantity(remain);
            child.setPromisedDays(ws.getPromisedDays());
            child.setPromisedDate(ws.getPromisedDate());
            child.setAmount(A.subtract(deliveredPay).max(BigDecimal.ZERO));
            child.setEscrowStatus("NONE");
            child.setActualProgress(0);
            child.setPeriodNo(ws.getPeriodNo());
            child.setPeriodStart(LocalDateTime.now());
            child.setPeriodEnd(ws.getPeriodEnd());
            child.setStatus("IN_PRODUCTION");
            child.setReworkCount(1);
            child.setReworkKind("QTY");
            child.setReworkDeadlineAt(LocalDateTime.now().plusHours(hours));
            child.setReworkReason(buildReworkReason(report));
            child.setParentStageId(ws.getId());
            child.setInspectFeeStatus("NONE");
            workStageMapper.insert(child);
            siteNotify.send(ws.getTenantId(), "请补件#" + child.getId(),
                    "买家要求数量返工。已交 " + delivered + " 件将托管工费；剩余 " + remain + " 件请在 "
                            + hours + " 小时内交付。剩余件由买家付质检费。");
        }
        siteNotify.send(d.getTenantId(), "数量返工#" + ws.getId(),
                "已交部分请支付托管 ¥" + deliveredPay + "；剩余 " + remain + " 件已生成补件工单。");
        auditService.record("数量返工", "STAGE", ws.getId(), "已交" + delivered + " 剩余" + remain);
    }

    private void closeChain(WorkStage ws, Order o, Demand d) {
        List<WorkStage> mine = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, ws.getOrderId())
                .eq(WorkStage::getTenantId, ws.getTenantId()));
        BigDecimal left = BigDecimal.ZERO;
        int curNo = ws.getPeriodNo() == null ? 0 : ws.getPeriodNo();
        for (WorkStage s : mine) {
            if ("CANCELLED".equals(s.getStatus()) || "CLOSED".equals(s.getStatus())
                    || "PASS".equals(s.getStatus()) || "COMPLETED".equals(s.getStatus())) {
                continue;
            }
            int no = s.getPeriodNo() == null ? 0 : s.getPeriodNo();
            boolean later = s.getId().equals(ws.getId())
                    || (curNo > 0 && no > curNo)
                    || (curNo <= 0 && s.getId() > ws.getId());
            if (!later) {
                continue;
            }
            left = left.add(s.getAmount() == null ? BigDecimal.ZERO : s.getAmount());
        }
        BigDecimal penalty = InspectRules.closePenalty(left);
        BigDecimal paid = fundLedger.payConcessionToBuyer(
                o.getId(), d.getId(), ws.getTenantId(), d.getTenantId(), ws.getId(), penalty);
        for (WorkStage s : mine) {
            int no = s.getPeriodNo() == null ? 0 : s.getPeriodNo();
            boolean later = !s.getId().equals(ws.getId())
                    && ((curNo > 0 && no > curNo) || (curNo <= 0 && s.getId() > ws.getId()));
            if (!later) {
                continue;
            }
            if ("PASS".equals(s.getStatus()) || "CLOSED".equals(s.getStatus()) || "CANCELLED".equals(s.getStatus())) {
                continue;
            }
            s.setStatus("CANCELLED");
            s.setPayAmount(BigDecimal.ZERO);
            s.setUpdatedAt(LocalDateTime.now());
            workStageMapper.updateById(s);
        }
        ws.setStatus("CLOSED");
        ws.setPayAmount(BigDecimal.ZERO);
        ws.setUpdatedAt(LocalDateTime.now());
        workStageMapper.updateById(ws);
        siteNotify.send(ws.getTenantId(), "本段已关闭#" + ws.getId(),
                "买家关闭本阶段，后续期已取消。已按当前+后续工费 5% 赔付买家 ¥" + paid + "。本期不交托管。");
        siteNotify.send(d.getTenantId(), "已关闭工单#" + ws.getId(),
                "本段已关闭并取消该厂后续期，赔付 ¥" + paid + "。本期不交托管款。");
        auditService.record("关闭连锁", "STAGE", ws.getId(), "赔付" + paid + " 基数" + left);
    }

    @Transactional
    public com.dsh.platform.domain.pay.EscrowStart payStage(Long stageId) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = demandMapper.selectById(o.getDemandId());
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能支付自己的订单阶段款");
        }
        if (!"PASS".equals(ws.getStatus())) {
            throw new BizException("仅可收款的阶段可以支付");
        }
        if (!"NONE".equals(ws.getEscrowStatus()) && !"PENDING_PAY".equals(ws.getEscrowStatus())) {
            throw new BizException("该阶段已托管或已结算");
        }
        BigDecimal amount = payableNow(ws);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("阶段金额无效");
        }
        // 尾款抵扣：最后一笔未付阶段款用买家保证金冲抵
        Long unpaid = workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, ws.getOrderId())
                .in(WorkStage::getEscrowStatus, "NONE", "PENDING_PAY"));
        if (unpaid != null && unpaid <= 1) {
            BigDecimal offset = fundLedger.applyBuyerDepositToEscrow(
                    o.getId(), d.getId(), d.getTenantId(), ws.getId(), amount);
            if (offset.compareTo(BigDecimal.ZERO) > 0) {
                d.setBuyerDepositStatus("DEDUCTED");
                demandMapper.updateById(d);
                amount = amount.subtract(offset);
                siteNotify.send(d.getTenantId(), "保证金已抵扣尾款#" + ws.getId(),
                        "你的保证金 ¥" + offset + " 已抵扣本阶段尾款，还需支付 ¥" + amount + "。");
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                ws.setEscrowStatus("HELD");
                workStageMapper.updateById(ws);
                creditScoring.onPaid(ws, d.getTenantId());
                return new com.dsh.platform.domain.pay.EscrowStart(true, null);
            }
        }
        var started = paymentChannel.startEscrow(o.getId(), ws.getId(), d.getTenantId(), amount);
        ws.setEscrowStatus(started.held() ? "HELD" : "PENDING_PAY");
        workStageMapper.updateById(ws);
        if (started.held()) {
            creditScoring.onPaid(ws, d.getTenantId());
        }
        return started;
    }

    /** 支付宝回调入账。幂等：已托管则直接返回。 */
    @Transactional
    public void confirmAlipayPaid(Long stageId, BigDecimal paidAmount) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        if ("HELD".equals(ws.getEscrowStatus()) || "SETTLED".equals(ws.getEscrowStatus())) {
            return;
        }
        BigDecimal expect = payableNow(ws);
        if (paidAmount != null && paidAmount.subtract(expect).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("回调金额与阶段款不一致");
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = demandMapper.selectById(o.getDemandId());
        paymentChannel.escrowStage(o.getId(), ws.getId(), d.getTenantId(), expect);
        ws.setEscrowStatus("HELD");
        workStageMapper.updateById(ws);
        creditScoring.onPaid(ws, d.getTenantId());
    }

    @Transactional
    public void accept(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        Demand d = demandMapper.selectById(o.getDemandId());
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能验收自己的订单");
        }
        List<WorkStage> stages = workStageMapper.selectList(new LambdaQueryWrapper<WorkStage>()
                .eq(WorkStage::getOrderId, orderId));
        if (!contractService.allSigned(orderId)) {
            throw new BizException("还有工厂的合同未审过，不能完工确认");
        }
        if (stages.isEmpty()) {
            throw new BizException("还没有工单");
        }
        for (WorkStage ws : stages) {
            if ("CLOSED".equals(ws.getStatus()) || "CANCELLED".equals(ws.getStatus())) {
                continue;
            }
            if (!"PASS".equals(ws.getStatus())) {
                throw new BizException("仍有工单未完成质检或买家未处理不合格结果，不能验收");
            }
            if (!"HELD".equals(ws.getEscrowStatus())) {
                throw new BizException("仍有可收款阶段未支付托管，不能验收");
            }
        }
        Map<Long, BigDecimal> byFactory = new HashMap<>();
        for (WorkStage ws : stages) {
            if ("CLOSED".equals(ws.getStatus()) || "CANCELLED".equals(ws.getStatus())) {
                continue;
            }
            byFactory.merge(ws.getTenantId(), payableNow(ws), BigDecimal::add);
        }
        BigDecimal total = byFactory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map.Entry<Long, BigDecimal> e : byFactory.entrySet()) {
            BigDecimal wages = e.getValue();
            BigDecimal commission = total.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : o.getCommissionAmount().multiply(wages).divide(total, 2, RoundingMode.HALF_UP);
            if (commission.compareTo(wages) > 0) {
                commission = wages;
            }
            paymentChannel.settleToFactory(orderId, e.getKey(), wages.subtract(commission));
            paymentChannel.takeCommission(orderId, o.getDemandId(), e.getKey(), commission);
        }
        for (WorkStage ws : stages) {
            ws.setEscrowStatus("SETTLED");
            workStageMapper.updateById(ws);
        }
        o.setStatus("COMPLETED");
        orderMapper.updateById(o);
        // 保证金若有剩余（抵扣后余额或未触发抵扣），完工时退还买家
        BigDecimal depositLeft = fundLedger.buyerDepositRemaining(d.getId(), d.getTenantId());
        if (depositLeft.compareTo(BigDecimal.ZERO) > 0) {
            fundLedger.refundBuyerDeposit(d.getId(), d.getTenantId());
            siteNotify.send(d.getTenantId(), "保证金已退还#" + orderId,
                    "订单完成，剩余保证金 ¥" + depositLeft + " 已退回。");
        }
        if (!"DEDUCTED".equals(d.getBuyerDepositStatus())) {
            d.setBuyerDepositStatus("RELEASED");
        }
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st == DemandStatus.CONTRACTED) {
            stateMachine.transit(d, DemandStatus.IN_PRODUCTION);
            demandMapper.updateById(d);
        }
        stateMachine.transit(d, DemandStatus.COMPLETED);
        demandMapper.updateById(d);
        creditScoring.applyOnComplete(o);
        siteNotify.send(d.getTenantId(), "订单已完成#" + orderId, "完工确认完成，工厂工钱已结算，平台佣金已从托管工钱扣除。");
        byFactory.keySet().forEach(fid -> siteNotify.send(fid, "订单已结算#" + orderId, "工钱已到账，佣金已从托管工钱扣除，剩余保证金已退回。"));
    }

    @Transactional
    public void submitSurvey(Long stageId, SurveyRequest req) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        if (!"PASS".equals(ws.getStatus())) {
            throw new BizException("仅质检通过的工单可以评价，关闭订单不用评分");
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = demandMapper.selectById(o.getDemandId());
        String role = UserContext.role();
        if ("BUYER".equals(role)) {
            if (!UserContext.tenantId().equals(d.getTenantId())) {
                throw new BizException(403, "只能评价自己的订单");
            }
        } else if ("FACTORY".equals(role)) {
            if (!UserContext.tenantId().equals(ws.getTenantId())) {
                throw new BizException(403, "只能评价自己的工单");
            }
        } else {
            throw new BizException(403, "仅买家和工厂可填问卷");
        }
        if (req == null || req.scores() == null) {
            throw new BizException("请给出总体评价星级");
        }
        Integer star = req.scores().get("overall");
        if (star == null) {
            star = req.scores().get("q4");
        }
        if (star == null || star < 1 || star > 5) {
            throw new BizException("总体评价须为 1～5 星");
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("overall", star);
        Long exists = surveyMapper.selectCount(new LambdaQueryWrapper<Survey>()
                .eq(Survey::getStageId, stageId)
                .eq(Survey::getRole, role)
                .eq(Survey::getTenantId, UserContext.tenantId()));
        if (exists != null && exists > 0) {
            throw new BizException("该阶段问卷已提交");
        }
        Survey s = new Survey();
        s.setStageId(stageId);
        s.setOrderId(ws.getOrderId());
        s.setTenantId(UserContext.tenantId());
        s.setRole(role);
        s.setScoresJson(writeJson(scores));
        surveyMapper.insert(s);
        creditScoring.onSurveySubmitted(ws, d.getTenantId());
    }

    public List<Survey> surveysOfStage(Long stageId) {
        return surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                .eq(Survey::getStageId, stageId)
                .orderByAsc(Survey::getId));
    }

    public Order get(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        return o;
    }

    private Inspection latestInspection(Long stageId) {
        return inspectionMapper.selectOne(new LambdaQueryWrapper<Inspection>()
                .eq(Inspection::getStageId, stageId)
                .orderByDesc(Inspection::getId)
                .last("limit 1"));
    }

    private static String buildReworkReason(Map<String, Object> report) {
        List<String> parts = new ArrayList<>();
        if (!Boolean.TRUE.equals(report.get("quantityOk"))) {
            parts.add("数量不达标");
        }
        int dc = mapInt(report, "criticalFailCount");
        int dg = mapInt(report, "generalFailCount");
        if (dc > 0) {
            parts.add("关键公差不合格 " + dc + " 件");
        }
        if (Boolean.FALSE.equals(report.get("toleranceOk")) && dc <= 0) {
            Object ac = report.get("aqlAc");
            if (ac != null) {
                parts.add("一般缺陷 " + dg + " 件，超过 Ac=" + ac);
            } else {
                parts.add("抽检良率低于最低良率");
            }
        } else if (dg > 0) {
            parts.add("一般公差不合格 " + dg + " 件");
        }
        Object y = report.get("actualYield");
        if (y != null) {
            parts.add("抽检良率 " + y);
        }
        Object remark = report.get("remark");
        if (remark != null && !remark.toString().isBlank()) {
            parts.add("质检备注：" + remark);
        }
        Object kd = report.get("keyDimensions");
        if (kd != null && !kd.toString().isBlank()) {
            parts.add("关键尺寸：" + kd);
        }
        return parts.isEmpty() ? "买家要求返工" : String.join("；", parts);
    }

    private void rememberFirstInspect(WorkStage ws, Map<String, Object> report) {
        if (nvlInt(ws.getInspectRound()) > 0) {
            return;
        }
        int n = mapInt(report, "sampleCount");
        int dc = mapInt(report, "criticalFailCount");
        int dg = mapInt(report, "generalFailCount");
        ws.setFirstYield(InspectRules.yield(n, dc, dg));
        ws.setFirstQuantityOk(Boolean.TRUE.equals(report.get("quantityOk")) ? 1 : 0);
    }

    private BigDecimal payableOnPass(WorkStage ws, Map<String, Object> report) {
        BigDecimal A = stageAmount(ws);
        if (nvlInt(ws.getReworkCount()) <= 0) {
            return A;
        }
        if (Integer.valueOf(0).equals(ws.getFirstQuantityOk())) {
            return A;
        }
        BigDecimal y1 = ws.getFirstYield();
        if (y1 == null) {
            int n = mapInt(report, "sampleCount");
            y1 = InspectRules.yield(n, mapInt(report, "criticalFailCount"), mapInt(report, "generalFailCount"));
        }
        return InspectRules.scale(A, y1);
    }

    private BigDecimal payableNow(WorkStage ws) {
        if (ws.getPayAmount() != null) {
            return ws.getPayAmount();
        }
        return stageAmount(ws);
    }

    private BigDecimal stageAmount(WorkStage ws) {
        return ws.getAmount() == null ? BigDecimal.ZERO : ws.getAmount();
    }

    private static int nvlInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static int mapInt(Map<String, Object> m, String key) {
        if (m == null || m.get(key) == null) {
            return 0;
        }
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> readCombo(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("方案数据解析失败");
        }
    }

    private Map<String, Object> readReport(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {});
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Transactional
    public void timeoutContractIssue() {
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "CREATED")
                .isNotNull(Order::getContractIssueEndAt)
                .le(Order::getContractIssueEndAt, LocalDateTime.now())
                .isNull(Order::getContractSignEndAt));
        for (Order o : list) {
            try {
                List<Contract> cs = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getOrderId, o.getId()));
                boolean missing = cs.stream().anyMatch(c -> c.getAttachmentId() == null);
                if (!missing) {
                    continue;
                }
                forfeitBuyerAllToCombo(o, "ISSUE-TIMEOUT-" + o.getId(),
                        "买家未在 48 小时内发布全部合同，已扣除全部保证金按方案件数比重赔偿工厂。");
            } catch (Exception ignored) {
                // 单笔失败不影响其它订单
            }
        }
    }

    @Transactional
    public void timeoutContractSign() {
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "CREATED")
                .isNotNull(Order::getContractSignEndAt)
                .le(Order::getContractSignEndAt, LocalDateTime.now()));
        for (Order o : list) {
            Demand d = demandMapper.selectById(o.getDemandId());
            if (d == null) {
                continue;
            }
            List<Contract> cs = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getOrderId, o.getId()));
            for (Contract c : cs) {
                if ("SIGNED".equals(c.getStatus()) || "CANCELLED".equals(c.getStatus())) {
                    continue;
                }
                boolean factorySigned = c.getFactorySign() != null && !c.getFactorySign().isBlank();
                if (c.getAttachmentId() != null && !factorySigned) {
                    dropFactoryFromSign(o, d, c, "签署期超时未签");
                }
            }
        }
    }

    @Transactional
    public void cancelByBuyer(Long orderId, String reason) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        Demand d = demandMapper.selectById(o.getDemandId());
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能取消自己的订单");
        }
        if (!"CREATED".equals(o.getStatus())) {
            throw new BizException("合同已生效，不能整单取消");
        }
        if (o.getContractSignEndAt() == null) {
            throw new BizException("仅合同签署期内可取消订单");
        }
        forfeitBuyerAllToCombo(o, "BUYER-CANCEL-" + o.getId(),
                reason == null || reason.isBlank()
                        ? "买家在签署期取消订单，已扣除全部保证金按方案件数比重赔偿工厂。"
                        : reason.trim());
    }

    @Transactional
    public void cancelByFactory(Long orderId, String reason) {
        if (!"FACTORY".equals(UserContext.role())) {
            throw new BizException(403, "仅中标工厂可取消");
        }
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        Demand d = demandMapper.selectById(o.getDemandId());
        Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .eq(Contract::getTenantId, UserContext.tenantId())
                .last("limit 1"));
        if (c == null) {
            throw new BizException("你没有该订单合同");
        }
        if ("SIGNED".equals(c.getStatus())) {
            throw new BizException("合同已签署，不能取消");
        }
        if (o.getContractSignEndAt() == null) {
            throw new BizException("仅合同签署期内可取消订单");
        }
        dropFactoryFromSign(o, d, c, reason == null || reason.isBlank() ? "工厂取消订单" : reason.trim());
    }

    private void forfeitBuyerAllToCombo(Order o, String tag, String reason) {
        Demand d = demandMapper.selectById(o.getDemandId());
        Solution s = solutionMapper.selectById(o.getSolutionId());
        Map<Long, Integer> weights = comboQtyWeights(s);
        BigDecimal remain = fundLedger.buyerDepositRemaining(d.getId(), d.getTenantId());
        fundLedger.splitBuyerDepositToFactories(d.getId(), d.getTenantId(), o.getId(), remain, weights, tag);
        fundLedger.unfreezeIntentionsOfDemand(d.getId());
        fundLedger.unfreezeDepositsOfDemand(d.getId());
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st == DemandStatus.SOLUTION_SELECTED || st == DemandStatus.CONTRACTED) {
            stateMachine.transit(d, DemandStatus.CANCELLED);
        }
        d.setCancelReason(reason);
        demandMapper.updateById(d);
        o.setStatus("CANCELLED");
        orderMapper.updateById(o);
        List<Contract> cs = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, o.getId()));
        for (Contract c : cs) {
            if (!"SIGNED".equals(c.getStatus())) {
                c.setStatus("CANCELLED");
                contractMapper.updateById(c);
            }
        }
        siteNotify.send(d.getTenantId(), "订单已取消#" + o.getId(), reason);
        for (Long fid : weights.keySet()) {
            siteNotify.send(fid, "买家违约赔偿#" + o.getId(),
                    "需求「" + d.getTitle() + "」" + reason);
        }
        auditService.record("合同期买家取消/逾期", "ORDER", o.getId(), reason);
    }

    private void dropFactoryFromSign(Order o, Demand d, Contract c, String reason) {
        fundLedger.payFactoryDepositToBuyer(d.getId(), c.getTenantId(), d.getTenantId(), o.getId(),
                "FACTORY-DROP-" + o.getId());
        c.setStatus("CANCELLED");
        contractMapper.updateById(c);
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getTenantId, c.getTenantId()));
        for (Quotation q : qs) {
            q.setStatus("LOSE");
            quotationMapper.updateById(q);
        }
        siteNotify.send(c.getTenantId(), "合同取消已扣保证金#" + o.getId(),
                "需求「" + d.getTitle() + "」" + reason + "，保证金已赔偿买家。其它厂合同继续。");
        siteNotify.send(d.getTenantId(), "工厂未签已获赔偿#" + o.getId(),
                "工厂未签署或取消合同，已将其保证金赔偿给你。其它工厂合同继续。");
        auditService.record("工厂未签/取消合同", "CONTRACT", c.getId(), reason);
    }

    private Map<Long, Integer> comboQtyWeights(Solution s) {
        Map<Long, Integer> weights = new LinkedHashMap<>();
        if (s == null) {
            return weights;
        }
        for (Map<String, Object> item : readCombo(s.getFinalComboJson())) {
            Object fid = item.get("factoryId");
            if (fid == null) {
                continue;
            }
            int qty = 0;
            Object q = item.get("quantity");
            if (q != null) {
                qty = new BigDecimal(q.toString()).intValue();
            }
            Long id = Long.valueOf(fid.toString());
            weights.merge(id, qty, Integer::max);
        }
        return weights;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
