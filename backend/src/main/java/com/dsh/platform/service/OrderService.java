package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.pay.PaymentChannel;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.dto.OrderDtos.InspectRequest;
import com.dsh.platform.dto.OrderDtos.ProgressRequest;
import com.dsh.platform.dto.OrderDtos.SurveyRequest;
import com.dsh.platform.entity.Attachment;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @Value("${dsh.fee.commission-rate}")
    private BigDecimal commissionRate;

    @Transactional
    public Long selectSolution(Long demandId, Long solutionId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("当前状态不可选方案");
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

        List<Map<String, Object>> combo = readCombo(s.getFinalComboJson());
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> c : combo) {
            total = total.add(new BigDecimal(c.get("price").toString()));
        }

        Order o = new Order();
        o.setDemandId(demandId);
        o.setSolutionId(solutionId);
        o.setTotalAmount(total);
        o.setCommissionRate(commissionRate);
        o.setCommissionAmount(total.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP));
        o.setStatus("CREATED");
        orderMapper.insert(o);

        contractService.createDrafts(o.getId(), s.getFinalComboJson());
        stateMachine.transit(d, DemandStatus.SOLUTION_SELECTED);
        demandMapper.updateById(d);

        for (Map<String, Object> c : combo) {
            Long fid = Long.valueOf(c.get("factoryId").toString());
            Integer pno = Integer.valueOf(c.get("processNo").toString());
            quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                    .eq(Quotation::getDemandId, demandId).eq(Quotation::getProcessNo, pno))
                    .forEach(q -> {
                        q.setStatus(q.getTenantId().equals(fid) ? "WIN" : "LOSE");
                        quotationMapper.updateById(q);
                    });
        }
        siteNotify.send(d.getTenantId(), "请按厂上传合同#" + o.getId(), "方案已选定。请按中标厂分别上传你们拟定的合同并签名。");
        combo.stream().map(c -> Long.valueOf(c.get("factoryId").toString())).distinct()
                .forEach(fid -> siteNotify.send(fid, "请签合同#" + o.getId(), "你已中标。买家上传与你的合同后，请阅读并签名。"));
        return o.getId();
    }

    @Transactional
    public void startStage(Long stageId) {
        WorkStage ws = requireFactoryStage(stageId);
        if (!"PENDING".equals(ws.getStatus())) {
            throw new BizException("只有待启动的工单可以开工");
        }
        if (!contractService.isSigned(ws.getOrderId(), ws.getTenantId())) {
            throw new BizException("与你的合同未双签或平台未审过，不能开工");
        }
        ws.setStatus("IN_PRODUCTION");
        workStageMapper.updateById(ws);
    }

    @Transactional
    public void reportProgress(Long stageId, ProgressRequest req) {
        WorkStage ws = requireFactoryStage(stageId);
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            throw new BizException("请先点开工再上报进度");
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

    public void deliver(Long stageId) {
        WorkStage ws = requireFactoryStage(stageId);
        if (!"IN_PRODUCTION".equals(ws.getStatus())) {
            throw new BizException("请先点开工并上报进度");
        }
        if (!contractService.isSigned(ws.getOrderId(), ws.getTenantId())) {
            throw new BizException("与你的合同未双签或平台未审过，不能交付");
        }
        ws.setStatus("PENDING_INSPECTION");
        ws.setActualProgress(100);
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
        if (req == null || req.result() == null) {
            throw new BizException("请选择合格或不合格");
        }
        String result = req.result();
        if (!"PASS".equals(result) && !"FAIL".equals(result)) {
            throw new BizException("质检结果只能是 PASS 或 FAIL");
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || !"PENDING_INSPECTION".equals(ws.getStatus())) {
            throw new BizException("工单状态不可质检");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sampleCount", req.sampleCount() == null ? 0 : req.sampleCount());
        report.put("failCount", req.failCount() == null ? 0 : req.failCount());
        report.put("keyDimensions", req.keyDimensions() == null ? "" : req.keyDimensions());
        report.put("meetsRequirement", Boolean.TRUE.equals(req.meetsRequirement()));
        report.put("remark", req.remark() == null ? "" : req.remark());
        String reportJson = writeJson(report);

        Inspection ins = new Inspection();
        ins.setStageId(stageId);
        ins.setInspectorTenantId(UserContext.tenantId());
        ins.setResult(result);
        ins.setReportJson(reportJson);
        ins.setHash(sha256(result + reportJson));
        ins.setStatus("VALID");
        inspectionMapper.insert(ins);

        ws.setStatus("PASS".equals(result) ? "PASS" : "FAIL");
        workStageMapper.updateById(ws);

        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
        if ("PASS".equals(result)) {
            if (d != null) {
                siteNotify.send(d.getTenantId(), "请付阶段款#" + stageId,
                        "工单「" + ws.getProcessName() + "」质检合格，请支付本阶段工钱（托管平台，不直接给工厂）。");
            }
        } else {
            if (d != null) {
                siteNotify.send(d.getTenantId(), "阶段不合格#" + stageId,
                        "工单「" + ws.getProcessName() + "」质检不合格，本阶段不收款。请填写阶段问卷。");
            }
        }
        if (d != null) {
            siteNotify.send(d.getTenantId(), "阶段问卷#" + stageId, "请对本阶段填写 4 题问卷。");
        }
        siteNotify.send(ws.getTenantId(), "阶段问卷#" + stageId, "请对本阶段填写 4 题问卷。");
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
            throw new BizException("仅质检合格的阶段可以支付");
        }
        if (!"NONE".equals(ws.getEscrowStatus()) && !"PENDING_PAY".equals(ws.getEscrowStatus())) {
            throw new BizException("该阶段已托管或已结算");
        }
        BigDecimal amount = ws.getAmount() == null ? BigDecimal.ZERO : ws.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("阶段金额无效");
        }
        var started = paymentChannel.startEscrow(o.getId(), ws.getId(), d.getTenantId(), amount);
        ws.setEscrowStatus(started.held() ? "HELD" : "PENDING_PAY");
        workStageMapper.updateById(ws);
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
        BigDecimal expect = ws.getAmount() == null ? BigDecimal.ZERO : ws.getAmount();
        if (paidAmount != null && paidAmount.subtract(expect).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("回调金额与阶段款不一致");
        }
        Order o = orderMapper.selectById(ws.getOrderId());
        Demand d = demandMapper.selectById(o.getDemandId());
        paymentChannel.escrowStage(o.getId(), ws.getId(), d.getTenantId(), expect);
        ws.setEscrowStatus("HELD");
        workStageMapper.updateById(ws);
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
            if (!"PASS".equals(ws.getStatus())) {
                throw new BizException("仍有工单未质检合格，不能验收");
            }
            if (!"HELD".equals(ws.getEscrowStatus())) {
                throw new BizException("仍有合格阶段未支付托管，不能验收");
            }
        }
        Map<Long, BigDecimal> byFactory = new HashMap<>();
        for (WorkStage ws : stages) {
            byFactory.merge(ws.getTenantId(), ws.getAmount() == null ? BigDecimal.ZERO : ws.getAmount(), BigDecimal::add);
        }
        BigDecimal total = byFactory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map.Entry<Long, BigDecimal> e : byFactory.entrySet()) {
            paymentChannel.settleToFactory(orderId, e.getKey(), e.getValue());
            BigDecimal commission = total.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : o.getCommissionAmount().multiply(e.getValue()).divide(total, 2, RoundingMode.HALF_UP);
            paymentChannel.takeCommission(orderId, o.getDemandId(), e.getKey(), commission);
        }
        for (WorkStage ws : stages) {
            ws.setEscrowStatus("SETTLED");
            workStageMapper.updateById(ws);
        }
        o.setStatus("COMPLETED");
        orderMapper.updateById(o);
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st == DemandStatus.CONTRACTED) {
            stateMachine.transit(d, DemandStatus.IN_PRODUCTION);
            demandMapper.updateById(d);
        }
        stateMachine.transit(d, DemandStatus.COMPLETED);
        demandMapper.updateById(d);
        creditScoring.applyOnComplete(o);
        siteNotify.send(d.getTenantId(), "订单已完成#" + orderId, "完工确认完成，工厂工钱已结算，佣金已从保证金扣除。");
        byFactory.keySet().forEach(fid -> siteNotify.send(fid, "订单已结算#" + orderId, "工钱已到账，佣金已从保证金扣除。"));
    }

    @Transactional
    public void submitSurvey(Long stageId, SurveyRequest req) {
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            throw new BizException("工单不存在");
        }
        if (!"PASS".equals(ws.getStatus()) && !"FAIL".equals(ws.getStatus())) {
            throw new BizException("阶段尚未结束，不能填问卷");
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
            throw new BizException("请给 4 题打分");
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String k : List.of("q1", "q2", "q3", "q4")) {
            Integer v = req.scores().get(k);
            if (v == null || v < 1 || v > 5) {
                throw new BizException("每题须为 1～5 分");
            }
            scores.put(k, v);
        }
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

    private List<Map<String, Object>> readCombo(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BizException("方案数据解析失败");
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
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
