package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.credit.CreditScoring;
import com.dsh.platform.domain.fund.FundLedger;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.solution.SolutionCombo;
import com.dsh.platform.domain.solution.SolutionGenerator;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.domain.solution.AiSolutionGenerator;
import com.dsh.platform.domain.solution.RuleSolutionGenerator;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方案编排：算法在 SolutionGenerator，这里只负责落库和改状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionService {

    private final DemandMapper demandMapper;
    private final SolutionMapper solutionMapper;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused")
    private final SolutionGenerator solutionGenerator;
    private final ProcessMapper processMapper;
    private final DemandStateMachine stateMachine;
    private final FundLedger fundLedger;
    private final CreditScoring creditScoring;
    private final SiteNotify siteNotify;
    private final RuleSolutionGenerator ruleSolutionGenerator;
    private final AiSolutionGenerator aiSolutionGenerator;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DeviceService deviceService;

    @Transactional
    public List<Solution> generate(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st == DemandStatus.SOLUTION_GENERATED) {
            return listByDemand(demandId);
        }
        if (st != DemandStatus.LOCKING) {
            throw new BizException("仅保证金期可以生成方案");
        }
        if (!coveredByLocked(d)) {
            stateMachine.transit(d, DemandStatus.FLOW_FAILED);
            demandMapper.updateById(d);
            fundLedger.unfreezeIntentionsOfDemand(demandId);
            fundLedger.unfreezeDepositsOfDemand(demandId);
            deviceService.releaseByDemand(demandId);
            siteNotify.send(d.getTenantId(), "需求流拍#" + demandId,
                    "需求「" + d.getTitle() + "」各工序没有足够的锁定报价，已流拍并退回意向金/保证金。");
            return List.of();
        }
        for (Quotation q : fundLedger.forfeitUnlockedIntentions(demandId)) {
            creditScoring.applyNoLock(q.getTenantId(), demandId);
            siteNotify.send(q.getTenantId(), "未锁价扣除意向金#" + demandId,
                    "需求「" + d.getTitle() + "」保证金期已结束，你未锁定报价，意向金已扣除并记失信。");
        }
        stateMachine.transit(d, DemandStatus.SOLUTION_GENERATED);
        demandMapper.updateById(d);
        tryInsertAi(d);
        return listByDemand(demandId);
    }

    private boolean coveredByLocked(Demand d) {
        List<Process> processes = processMapper.selectList(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, d.getId())
                .orderByAsc(Process::getProcessNo));
        if (processes.isEmpty()) {
            Process one = new Process();
            one.setProcessNo(1);
            one.setQuantity(d.getQuantity());
            processes = List.of(one);
        }
        List<Quotation> locked = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getStatus, "LOCKED"));
        for (Process p : processes) {
            int need = p.getQuantity() == null ? 0 : p.getQuantity();
            Integer no = p.getProcessNo() == null ? 1 : p.getProcessNo();
            boolean ok = locked.stream()
                    .filter(q -> no.equals(q.getProcessNo() == null ? 1 : q.getProcessNo()))
                    .anyMatch(q -> q.getMaxQty() != null && q.getMaxQty() >= need);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private void tryInsertAi(Demand d) {
        try {
            List<SolutionCombo> combos = aiSolutionGenerator.generate(d);
            insertAiCombos(d.getId(), combos);
            siteNotify.notifyOperators("AI方案待审核#" + d.getId(),
                    "需求「" + d.getTitle() + "」的 AI 方案已生成，请审阅后下发给买家。");
        } catch (Exception e) {
            log.warn("AI 方案生成失败 demandId={}: {}", d.getId(), e.getMessage());
            siteNotify.notifyOperators("AI方案生成失败#" + d.getId(),
                    "需求「" + d.getTitle() + "」保证金期已结束，AI 方案生成失败："
                            + e.getMessage() + "。请到需求管理手动重试。");
        }
    }

    private void insertAiCombos(Long demandId, List<SolutionCombo> combos) {
        if (combos == null) {
            return;
        }
        for (SolutionCombo combo : combos) {
            Solution s = new Solution();
            s.setDemandId(demandId);
            s.setType(combo.type());
            s.setSuggestedComboJson(writeJson(combo.items()));
            s.setFinalComboJson(writeJson(combo.items()));
            s.setSource("AI");
            s.setIsFinal(0);
            s.setScore(combo.score());
            s.setRationaleJson(combo.rationale());
            s.setStatus("PENDING_REVIEW");
            solutionMapper.insert(s);
        }
    }

    public List<Solution> listByDemand(Long demandId) {
        LambdaQueryWrapper<Solution> q = new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId);
        String role = UserContext.role();
        if (!"OPERATOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
            q.eq(Solution::getStatus, "ACTIVE");
        }
        List<Solution> list = solutionMapper.selectList(q);
        list.sort((a, b) -> Integer.compare(typeOrder(a.getType()), typeOrder(b.getType())));
        for (Solution s : list) {
            s.setFinalComboJson(writeJson(enrichCombo(s.getFinalComboJson())));
            s.setSuggestedComboJson(writeJson(enrichCombo(s.getSuggestedComboJson())));
        }
        return list;
    }

    public List<Solution> generateAi(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        if (!"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案已生成后可以重试 AI 方案");
        }
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以生成并审核 AI 方案");
        }
        List<Solution> existing = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId)
                .likeRight(Solution::getType, "AI"));
        if (!existing.isEmpty()) {
            return listByDemand(demandId);
        }
        List<SolutionCombo> combos = aiSolutionGenerator.generate(d);
        insertAiCombos(d.getId(), combos);
        return listByDemand(demandId);
    }

    @Transactional
    public void publishToBuyer(Long solutionId) {
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以下发方案");
        }
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        Demand d = demandMapper.selectById(s.getDemandId());
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("当前状态不能下发");
        }
        if ("ACTIVE".equals(s.getStatus())) {
            return;
        }
        s.setStatus("ACTIVE");
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        solutionMapper.updateById(s);
        String title = (s.getType() != null && s.getType().startsWith("AI"))
                ? "运营下发了AI方案#" + d.getId()
                : "方案已生成#" + d.getId();
        siteNotify.send(d.getTenantId(), title,
                "需求「" + d.getTitle() + "」的方案 " + s.getType() + " 已下发，可在方案页查看和选用。");
    }

    public List<Map<String, Object>> alternatives(Long solutionId, Integer processNo) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        Demand d = demandMapper.selectById(s.getDemandId());
        Map<String, Object> current = currentItem(s, processNo);
        int need = current.get("quantity") == null ? 0 : new BigDecimal(current.get("quantity").toString()).intValue();
        return ruleSolutionGenerator.alternatives(d, processNo, need);
    }

    @Transactional
    public void replaceFactory(Long solutionId, Integer processNo, Long factoryId) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        Demand d = demandMapper.selectById(s.getDemandId());
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案未选定前可以换厂");
        }
        String role = UserContext.role();
        boolean operator = "OPERATOR".equals(role) || "SUPER_ADMIN".equals(role);
        if (!operator && !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能改自己的需求方案");
        }
        if (!operator && !"ACTIVE".equals(s.getStatus())) {
            throw new BizException("该方案尚未下发");
        }
        if (processNo == null || factoryId == null) {
            throw new BizException("请选择工序和工厂");
        }
        List<Map<String, Object>> combo = enrichCombo(s.getFinalComboJson());
        Map<String, Object> item = combo.stream()
                .filter(m -> processNo.equals(asInt(m.get("processNo"))))
                .findFirst()
                .orElseThrow(() -> new BizException("方案中没有该工序"));
        int need = item.get("quantity") == null ? 0 : new BigDecimal(item.get("quantity").toString()).intValue();
        boolean allowed = ruleSolutionGenerator.alternatives(d, processNo, need).stream()
                .anyMatch(a -> factoryId.equals(asLong(a.get("factoryId"))));
        if (!allowed) {
            throw new BizException("该厂未锁定该工序或产能不足");
        }
        Long from = asLong(item.get("factoryId"));
        Object keptPrice = item.get("price");
        Quotation q = quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, d.getId())
                .eq(Quotation::getTenantId, factoryId)
                .eq(Quotation::getProcessNo, processNo)
                .eq(Quotation::getStatus, "LOCKED")
                .last("limit 1"));
        Enterprise e = enterpriseMapper.selectById(factoryId);
        item.put("factoryId", factoryId);
        item.put("factoryName", e == null ? ("厂" + factoryId) : e.getName());
        item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
        item.put("authStatus", e == null ? "" : e.getAuthStatus());
        item.put("price", keptPrice);
        if (q != null) {
            item.put("yieldRate", q.getYieldRate() == null ? BigDecimal.ZERO : q.getYieldRate());
            item.put("days", q.getPromisedDays() == null ? 0 : q.getPromisedDays());
            item.put("maxQty", q.getMaxQty());
        }
        List<Map<String, Object>> edits = readEdits(s.getEditedFieldsJson());
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("processNo", processNo);
        edit.put("from", from);
        edit.put("to", factoryId);
        edit.put("keptPrice", keptPrice);
        edits.add(edit);
        s.setFinalComboJson(writeJson(combo));
        s.setEditedFieldsJson(writeJson(edits));
        s.setSource("EDITED");
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        solutionMapper.updateById(s);
    }

    private Map<String, Object> currentItem(Solution s, Integer processNo) {
        return enrichCombo(s.getFinalComboJson()).stream()
                .filter(m -> processNo != null && processNo.equals(asInt(m.get("processNo"))))
                .findFirst()
                .orElseThrow(() -> new BizException("方案中没有该工序"));
    }

    private List<Map<String, Object>> enrichCombo(String json) {
        List<Map<String, Object>> combo = readCombo(json);
        for (Map<String, Object> item : combo) {
            Long fid = asLong(item.get("factoryId"));
            if (fid == null) {
                continue;
            }
            Enterprise e = enterpriseMapper.selectById(fid);
            if (e != null) {
                item.put("factoryName", e.getName());
                item.put("creditScore", e.getCreditScore());
                item.put("authStatus", e.getAuthStatus());
                item.put("certs", certsOf(e));
            }
        }
        return combo;
    }

    private String certsOf(Enterprise e) {
        if (e == null || e.getCapabilityJson() == null || e.getCapabilityJson().isBlank()) {
            return "";
        }
        try {
            var n = objectMapper.readTree(e.getCapabilityJson()).path("certs");
            if (n.isArray()) {
                List<String> names = new ArrayList<>();
                n.forEach(x -> names.add(x.asText()));
                return String.join(",", names);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private List<Map<String, Object>> readCombo(String json) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                copy.add(new LinkedHashMap<>(m));
            }
            return copy;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> readEdits(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Long asLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private static Integer asInt(Object v) {
        return v == null ? null : Integer.valueOf(new BigDecimal(v.toString()).intValue());
    }

    private static int typeOrder(String type) {
        return switch (type == null ? "" : type) {
            case "A" -> 1;
            case "B" -> 2;
            case "C" -> 3;
            case "AI1" -> 4;
            case "AI2" -> 5;
            case "AI3" -> 6;
            default -> 9;
        };
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
