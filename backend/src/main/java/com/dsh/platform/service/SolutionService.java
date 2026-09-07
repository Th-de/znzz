package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final SiteNotify siteNotify;
    private final RuleSolutionGenerator ruleSolutionGenerator;
    private final AiSolutionGenerator aiSolutionGenerator;
    private final QuotationMapper quotationMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DeviceService deviceService;
    private final AuditService auditService;

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
        for (Quotation q : fundLedger.forfeitUnlockedIntentions(demandId)) {
            siteNotify.send(q.getTenantId(), "未锁价扣除意向金#" + demandId,
                    "需求「" + d.getTitle() + "」保证金期已结束，你未锁定报价，意向金已扣除。");
        }
        stateMachine.transit(d, DemandStatus.SOLUTION_GENERATED);
        demandMapper.updateById(d);
        tryInsertAi(d);
        return listByDemand(demandId);
    }

    /** 新流程入口：买家交保证金后异步生成 AI 方案（失败通知运营重试）。 */
    @org.springframework.scheduling.annotation.Async
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void aiGenerateFor(Long demandId) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            return;
        }
        List<Solution> existing = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId)
                .likeRight(Solution::getType, "AI"));
        if (!existing.isEmpty()) {
            return;
        }
        tryInsertAi(d);
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
            s.setFinalComboJson(writeJson(enrichCombo(demandId, s.getFinalComboJson())));
            s.setSuggestedComboJson(writeJson(enrichCombo(demandId, s.getSuggestedComboJson())));
        }
        return list;
    }

    /** 买家自选工厂并分配件数，生成可确认的自编方案。 */
    @Transactional
    public Solution saveBuyerCustom(Long demandId, List<Map<String, Object>> items) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        if (!"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案核定期可以自选工厂");
        }
        if (!UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能操作自己的需求");
        }
        if (items == null || items.isEmpty()) {
            throw new BizException("请至少选择一家工厂并填写分量");
        }
        String processName = processRoute(demandId);
        int need = d.getQuantity() == null ? 0 : d.getQuantity();
        Map<Long, Integer> byFactory = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            Integer qty = asInt(it.get("quantity"));
            Long fid = asLong(it.get("factoryId"));
            if (fid == null || qty == null || qty <= 0) {
                throw new BizException("每行须选择工厂并填写大于 0 的件数");
            }
            if (byFactory.put(fid, qty) != null) {
                throw new BizException("同一工厂不能重复分配");
            }
        }
        int sum = byFactory.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != need) {
            throw new BizException("各厂分配件数合计 " + sum + "，必须等于需求零件数 " + need);
        }
        List<Map<String, Object>> combo = new ArrayList<>();
        for (Map.Entry<Long, Integer> alloc : byFactory.entrySet()) {
            Long fid = alloc.getKey();
            Integer qty = alloc.getValue();
            Enterprise e = enterpriseMapper.selectById(fid);
            String factoryName = e == null || e.getName() == null || e.getName().isBlank()
                    ? ("工厂" + fid) : e.getName();
            Quotation priced = factoryQuote(demandId, fid, "LOCKED", "WIN");
            if (priced == null) {
                throw new BizException("「" + factoryName + "」没有该需求的有效报价");
            }
            if (priced.getMinQty() != null && qty < priced.getMinQty()) {
                throw new BizException("「" + factoryName + "」分配量低于其最小承接量 " + priced.getMinQty());
            }
            if (priced.getMaxQty() != null && qty > priced.getMaxQty()) {
                throw new BizException("「" + factoryName + "」分配量超过其最大承接量 " + priced.getMaxQty());
            }
            BigDecimal unit = priced.getUnitPrice() != null ? priced.getUnitPrice()
                    : (priced.getPrice() != null && priced.getMaxQty() != null && priced.getMaxQty() > 0
                    ? priced.getPrice().divide(BigDecimal.valueOf(priced.getMaxQty()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            BigDecimal subtotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processNo", 1);
            item.put("processName", processName);
            item.put("factoryId", fid);
            item.put("factoryName", factoryName);
            item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
            item.put("unitPrice", unit);
            item.put("price", subtotal);
            item.put("yieldRate", priced.getYieldRate() == null ? BigDecimal.ZERO : priced.getYieldRate());
            item.put("days", priced.getPromisedDays() == null ? 0 : priced.getPromisedDays());
            item.put("quantity", qty);
            item.put("minQty", priced.getMinQty());
            item.put("maxQty", priced.getMaxQty());
            combo.add(item);
        }
        Solution s = solutionMapper.selectOne(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId)
                .eq(Solution::getType, "CUSTOM")
                .last("limit 1"));
        if (s == null) {
            s = new Solution();
            s.setDemandId(demandId);
            s.setType("CUSTOM");
            solutionMapper.insert(s);
        }
        s.setSuggestedComboJson(writeJson(combo));
        s.setFinalComboJson(writeJson(combo));
        s.setSource("BUYER");
        s.setIsFinal(0);
        s.setStatus("ACTIVE");
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        s.setRationaleJson(writeJson(Map.of("rationale", "买家自选工厂与件数", "risks", List.of(), "milestones", List.of())));
        solutionMapper.updateById(s);
        return s;
    }

    public List<Solution> generateAi(Long demandId) {
        return generateAi(demandId, false);
    }

    public List<Solution> generateAi(Long demandId, boolean force) {
        Demand d = demandMapper.selectById(demandId);
        if (d == null) {
            throw new BizException("需求不存在");
        }
        if (!"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案核定期可以生成 AI 方案");
        }
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以生成并审核 AI 方案");
        }
        List<Solution> existing = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId)
                .likeRight(Solution::getType, "AI"));
        if (!existing.isEmpty() && !force) {
            return listByDemand(demandId);
        }
        if (force) {
            for (Solution s : existing) {
                if (!"ACTIVE".equals(s.getStatus())) {
                    solutionMapper.deleteById(s.getId());
                }
            }
            existing = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                    .eq(Solution::getDemandId, demandId)
                    .likeRight(Solution::getType, "AI"));
            if (!existing.isEmpty()) {
                return listByDemand(demandId);
            }
        }
        List<SolutionCombo> combos = aiSolutionGenerator.generate(d);
        insertAiCombos(d.getId(), combos);
        return listByDemand(demandId);
    }

    /** 运营审阅时保存修改（推荐理由 + 各厂分配量）。 */
    @Transactional
    public List<Solution> saveReview(Long solutionId, Map<String, Object> body) {
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以修改待审方案");
        }
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        Demand d = demandMapper.selectById(s.getDemandId());
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案核定期可以修改");
        }
        if ("ACTIVE".equals(s.getStatus())) {
            throw new BizException("已下发的方案请让买家在自选页调整");
        }
        Object itemsObj = body == null ? null : body.get("items");
        if (itemsObj instanceof List<?> rawItems && !rawItems.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) (List<?>) rawItems;
            List<Map<String, Object>> alloc = new ArrayList<>();
            for (Map<String, Object> it : items) {
                Integer qty = asInt(it.get("quantity"));
                if (asLong(it.get("factoryId")) == null || qty == null || qty <= 0) {
                    continue;
                }
                alloc.add(it);
            }
            if (!alloc.isEmpty()) {
                reallocate(solutionId, 1, alloc);
            }
            s = solutionMapper.selectById(solutionId);
        }
        if (body != null && body.get("rationale") != null) {
            Map<String, Object> pack = new LinkedHashMap<>();
            try {
                if (s.getRationaleJson() != null && s.getRationaleJson().startsWith("{")) {
                    pack.putAll(objectMapper.readValue(s.getRationaleJson(), new TypeReference<Map<String, Object>>() {}));
                }
            } catch (Exception ignored) {
                pack.put("rationale", s.getRationaleJson());
            }
            pack.put("rationale", String.valueOf(body.get("rationale")));
            s.setRationaleJson(writeJson(pack));
        }
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        s.setSource("EDITED");
        solutionMapper.updateById(s);
        auditService.record("修改AI方案", "SOLUTION", solutionId, "需求#" + d.getId());
        return listByDemand(d.getId());
    }

    @Transactional
    public void publishToBuyer(Long solutionId) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        publishBatchToBuyer(s.getDemandId(), List.of(solutionId));
    }

    /** 勾选的方案一起 ACTIVE，同需求未勾选的待审方案标记 REJECTED，只通知买家一次。 */
    @Transactional
    public void publishBatchToBuyer(Long demandId, List<Long> solutionIds) {
        if (!"OPERATOR".equals(UserContext.role()) && !"SUPER_ADMIN".equals(UserContext.role())) {
            throw new BizException(403, "仅运营可以下发方案");
        }
        if (solutionIds == null || solutionIds.isEmpty()) {
            throw new BizException("请选择要下发的方案");
        }
        Demand d = demandMapper.selectById(demandId);
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("当前状态不能下发");
        }
        Set<Long> picked = new HashSet<>(solutionIds);
        List<Solution> all = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getDemandId, demandId));
        List<Solution> activate = new ArrayList<>();
        for (Solution s : all) {
            if (s.getType() != null && "CUSTOM".equals(s.getType())) {
                continue;
            }
            if (picked.contains(s.getId())) {
                if ("REJECTED".equals(s.getStatus()) || "PENDING_REVIEW".equals(s.getStatus())
                        || s.getStatus() == null || s.getStatus().isBlank()) {
                    activate.add(s);
                } else if (!"ACTIVE".equals(s.getStatus())) {
                    throw new BizException("方案 " + s.getType() + " 当前不能下发");
                }
            }
        }
        if (activate.isEmpty()) {
            boolean already = all.stream().anyMatch(s -> picked.contains(s.getId()) && "ACTIVE".equals(s.getStatus()));
            if (already) {
                throw new BizException("所选方案已经下发给买家");
            }
            throw new BizException("没有可下发的方案，请确认勾选的是待审方案");
        }
        LocalDateTime now = LocalDateTime.now();
        Long editor = UserContext.userId();
        List<String> types = new ArrayList<>();
        for (Solution s : activate) {
            s.setStatus("ACTIVE");
            s.setEditedBy(editor);
            s.setEditedAt(now);
            solutionMapper.updateById(s);
            types.add(s.getType() == null ? ("#" + s.getId()) : s.getType());
        }
        for (Solution s : all) {
            if (s.getType() != null && "CUSTOM".equals(s.getType())) {
                continue;
            }
            if (picked.contains(s.getId()) || "ACTIVE".equals(s.getStatus())) {
                continue;
            }
            if ("PENDING_REVIEW".equals(s.getStatus()) || s.getStatus() == null || s.getStatus().isBlank()) {
                s.setStatus("REJECTED");
                s.setEditedBy(editor);
                s.setEditedAt(now);
                solutionMapper.updateById(s);
            }
        }
        String typeText = String.join("、", types);
        siteNotify.send(d.getTenantId(), "推荐方案已审核#" + d.getId(),
                "需求「" + d.getTitle() + "」的推荐方案 " + typeText
                        + " 已一并审核下发。请参考推荐，按各厂承接件数区间自行分配后确认。");
        auditService.record("下发方案", "DEMAND", demandId,
                "一次下发 " + typeText + " 共" + activate.size() + "套");
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

    public void replaceFactory(Long solutionId, Integer processNo, Long factoryId) {
        replaceFactory(solutionId, processNo, factoryId, null);
    }

    @Transactional
    public void replaceFactory(Long solutionId, Integer processNo, Long factoryId, Long fromFactoryId) {
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
        List<Map<String, Object>> combo = enrichCombo(d.getId(), s.getFinalComboJson());
        Map<String, Object> item = combo.stream()
                .filter(m -> processNo.equals(asInt(m.get("processNo"))))
                .filter(m -> fromFactoryId == null || fromFactoryId.equals(asLong(m.get("factoryId"))))
                .findFirst()
                .orElseThrow(() -> new BizException("方案中没有该工序"));
        int need = item.get("quantity") == null ? 0 : new BigDecimal(item.get("quantity").toString()).intValue();
        boolean allowed = ruleSolutionGenerator.alternatives(d, processNo, need).stream()
                .anyMatch(a -> factoryId.equals(asLong(a.get("factoryId"))));
        if (!allowed) {
            throw new BizException("该厂未锁定报价或产能不足");
        }
        Long from = asLong(item.get("factoryId"));
        Quotation q = factoryQuote(d.getId(), factoryId, "LOCKED");
        Enterprise e = enterpriseMapper.selectById(factoryId);
        item.put("factoryId", factoryId);
        item.put("factoryName", e == null ? ("厂" + factoryId) : e.getName());
        item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
        item.put("authStatus", e == null ? "" : e.getAuthStatus());
        if (q != null) {
            BigDecimal unit = q.getUnitPrice() != null ? q.getUnitPrice()
                    : (q.getPrice() != null && q.getMaxQty() != null && q.getMaxQty() > 0
                    ? q.getPrice().divide(BigDecimal.valueOf(q.getMaxQty()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            item.put("unitPrice", unit);
            item.put("price", unit.multiply(BigDecimal.valueOf(need)).setScale(2, java.math.RoundingMode.HALF_UP));
            item.put("yieldRate", q.getYieldRate() == null ? BigDecimal.ZERO : q.getYieldRate());
            item.put("days", q.getPromisedDays() == null ? 0 : q.getPromisedDays());
            item.put("minQty", q.getMinQty());
            item.put("maxQty", q.getMaxQty());
        }
        List<Map<String, Object>> edits = readEdits(s.getEditedFieldsJson());
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("processNo", processNo);
        edit.put("from", from);
        edit.put("to", factoryId);
        edits.add(edit);
        s.setFinalComboJson(writeJson(combo));
        s.setEditedFieldsJson(writeJson(edits));
        s.setSource("EDITED");
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        solutionMapper.updateById(s);
    }

    /**
     * 买家/运营编辑方案：按厂重新分配零件件数。
     * 校验：各厂必须有有效报价，分配量落在承接区间内，合计 = 需求零件数。
     */
    @Transactional
    public void reallocate(Long solutionId, Integer processNo, List<Map<String, Object>> allocations) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        Demand d = demandMapper.selectById(s.getDemandId());
        if (d == null || !"SOLUTION_GENERATED".equals(d.getStatus())) {
            throw new BizException("仅方案确认前可以调整分配");
        }
        String role = UserContext.role();
        boolean operator = "OPERATOR".equals(role) || "SUPER_ADMIN".equals(role);
        if (!operator && !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "只能改自己的需求方案");
        }
        if (!operator && !"ACTIVE".equals(s.getStatus())) {
            throw new BizException("该方案尚未下发");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new BizException("请给出各厂分配明细");
        }
        int need = d.getQuantity() == null ? 0 : d.getQuantity();
        String processName = processRoute(d.getId());

        int sum = 0;
        List<Map<String, Object>> newItems = new ArrayList<>();
        for (Map<String, Object> a : allocations) {
            Long fid = asLong(a.get("factoryId"));
            Integer qty = asInt(a.get("quantity"));
            if (fid == null || qty == null || qty <= 0) {
                throw new BizException("分配明细须包含工厂和大于 0 的数量");
            }
            Quotation q = factoryQuote(d.getId(), fid, "LOCKED", "WIN");
            Enterprise e = enterpriseMapper.selectById(fid);
            String factoryName = e == null || e.getName() == null || e.getName().isBlank()
                    ? ("工厂" + fid) : e.getName();
            if (q == null) {
                throw new BizException("「" + factoryName + "」没有该需求的有效报价，不能分配");
            }
            if (q.getMinQty() != null && qty < q.getMinQty()) {
                throw new BizException("「" + factoryName + "」分配量低于其最小承接量 " + q.getMinQty());
            }
            if (q.getMaxQty() != null && qty > q.getMaxQty()) {
                throw new BizException("「" + factoryName + "」分配量超过其最大承接量 " + q.getMaxQty());
            }
            sum += qty;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processNo", 1);
            item.put("processName", processName);
            item.put("factoryId", fid);
            item.put("factoryName", e == null ? ("厂" + fid) : e.getName());
            item.put("creditScore", e == null || e.getCreditScore() == null ? 60 : e.getCreditScore());
            BigDecimal unit = q.getUnitPrice() != null ? q.getUnitPrice()
                    : (q.getPrice() != null && q.getMaxQty() != null && q.getMaxQty() > 0
                    ? q.getPrice().divide(BigDecimal.valueOf(q.getMaxQty()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            item.put("unitPrice", unit);
            item.put("price", unit.multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP));
            item.put("yieldRate", q.getYieldRate() == null ? BigDecimal.ZERO : q.getYieldRate());
            item.put("days", q.getPromisedDays() == null ? 0 : q.getPromisedDays());
            item.put("quantity", qty);
            item.put("minQty", q.getMinQty());
            item.put("maxQty", q.getMaxQty());
            newItems.add(item);
        }
        if (sum != need) {
            throw new BizException("各厂分配件数合计 " + sum + "，必须等于需求零件数 " + need);
        }
        List<Map<String, Object>> edits = readEdits(s.getEditedFieldsJson());
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("processNo", processNo);
        edit.put("reallocate", allocations);
        edits.add(edit);
        s.setFinalComboJson(writeJson(newItems));
        s.setEditedFieldsJson(writeJson(edits));
        s.setSource("EDITED");
        s.setEditedBy(UserContext.userId());
        s.setEditedAt(LocalDateTime.now());
        solutionMapper.updateById(s);
    }

    /** 可供分配的候选工厂：该需求已填报报价的厂及其承接区间、单价。 */
    public List<Map<String, Object>> allocationCandidates(Long solutionId, Integer processNo) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null) {
            throw new BizException("方案不存在");
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, s.getDemandId())
                .in(Quotation::getStatus, "LOCKED", "WIN"));
        Map<Long, Quotation> byFactory = new LinkedHashMap<>();
        for (Quotation q : qs) {
            if (q.getTenantId() != null) {
                byFactory.putIfAbsent(q.getTenantId(), q);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Quotation q : byFactory.values()) {
            Enterprise e = enterpriseMapper.selectById(q.getTenantId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("factoryId", q.getTenantId());
            m.put("factoryName", e == null ? ("厂" + q.getTenantId()) : e.getName());
            m.put("creditScore", e == null ? 60 : e.getCreditScore());
            m.put("unitPrice", q.getUnitPrice());
            m.put("maxQty", q.getMaxQty());
            m.put("minQty", q.getMinQty());
            m.put("yieldRate", q.getYieldRate());
            m.put("days", q.getPromisedDays());
            m.put("dailyCapacitySum", deviceService.dailyCapacitySum(
                    deviceService.byIds(deviceService.parseIds(q.getDeviceIdsJson()))));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> currentItem(Solution s, Integer processNo) {
        return enrichCombo(s.getDemandId(), s.getFinalComboJson()).stream()
                .filter(m -> processNo != null && processNo.equals(asInt(m.get("processNo"))))
                .findFirst()
                .orElseThrow(() -> new BizException("方案中没有该工序"));
    }

    private List<Map<String, Object>> enrichCombo(Long demandId, String json) {
        List<Map<String, Object>> combo = readCombo(json);
        if (combo.isEmpty()) {
            return combo;
        }
        List<Quotation> quotes = demandId == null ? List.of()
                : quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .in(Quotation::getStatus, "LOCKED", "WIN", "INTENTION"));
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
            Quotation q = quotes.stream()
                    .filter(x -> fid.equals(x.getTenantId()))
                    .min((a, b) -> Integer.compare(quoteRank(a.getStatus()), quoteRank(b.getStatus())))
                    .orElse(null);
            if (q != null) {
                item.put("minQty", q.getMinQty());
                item.put("maxQty", q.getMaxQty());
            }
        }
        return combo;
    }

    private static int quoteRank(String status) {
        if ("LOCKED".equals(status) || "WIN".equals(status)) {
            return 0;
        }
        if ("INTENTION".equals(status)) {
            return 1;
        }
        return 2;
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

    private Quotation factoryQuote(Long demandId, Long factoryId, String... statuses) {
        if (demandId == null || factoryId == null || statuses == null || statuses.length == 0) {
            return null;
        }
        return quotationMapper.selectOne(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryId)
                .in(Quotation::getStatus, statuses)
                .orderByDesc(Quotation::getId)
                .last("limit 1"));
    }

    private String processRoute(Long demandId) {
        List<Process> processes = processMapper.selectList(new LambdaQueryWrapper<Process>()
                .eq(Process::getDemandId, demandId)
                .orderByAsc(Process::getProcessNo));
        if (processes.isEmpty()) {
            return "全部工序";
        }
        List<String> names = new ArrayList<>();
        for (Process p : processes) {
            if (p.getProcessName() != null && !p.getProcessName().isBlank()) {
                names.add(p.getProcessName().trim());
            }
        }
        return names.isEmpty() ? "全部工序" : String.join("+", names);
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
            case "AI4" -> 7;
            case "AI5" -> 8;
            case "CUSTOM" -> 9;
            default -> 10;
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
