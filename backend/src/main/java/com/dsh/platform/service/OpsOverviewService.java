package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营工作台概览：一屏看全平台待办与在途，运营是系统的大脑。
 */
@Service
@RequiredArgsConstructor
public class OpsOverviewService {

    private final DemandMapper demandMapper;
    private final SolutionMapper solutionMapper;
    private final ContractMapper contractMapper;
    private final WorkStageMapper workStageMapper;
    private final EnterpriseMapper enterpriseMapper;

    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingAudit", countByStatus("PENDING_AUDIT"));
        m.put("published", countByStatus("PUBLISHED"));
        m.put("factoryThinking", countByStatus("FACTORY_THINKING"));
        m.put("buyerThinking", countByStatus("BUYER_THINKING"));
        m.put("solutionGenerated", countByStatus("SOLUTION_GENERATED"));
        m.put("solutionSelected", countByStatus("SOLUTION_SELECTED"));
        m.put("inProduction", demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .in(Demand::getStatus, "CONTRACTED", "IN_PRODUCTION")));
        m.put("completed", countByStatus("COMPLETED"));

        m.put("aiPendingReview", solutionMapper.selectCount(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getStatus, "PENDING_REVIEW")));
        m.put("contractsPending", contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "PENDING_REVIEW")));
        m.put("stagesPendingInspection", workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .in(WorkStage::getStatus, "PENDING_INSPECTION", "PENDING_REVIEW")));
        m.put("stagesOverdue", workStageMapper.selectCount(new LambdaQueryWrapper<WorkStage>()
                .in(WorkStage::getStatus, "PENDING", "IN_PRODUCTION")
                .isNotNull(WorkStage::getPromisedDate)
                .lt(WorkStage::getPromisedDate, java.time.LocalDate.now())));

        m.put("attention", attentionList());
        return m;
    }

    /** 需要运营立即处理/关注的需求清单（最新提交在上）。 */
    private List<Map<String, Object>> attentionList() {
        List<Demand> list = demandMapper.selectList(new LambdaQueryWrapper<Demand>()
                .in(Demand::getStatus, "PENDING_AUDIT", "PUBLISHED", "FACTORY_THINKING",
                        "BUYER_THINKING", "SOLUTION_GENERATED")
                .orderByDesc(Demand::getCreatedAt)
                .orderByDesc(Demand::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Demand d : list) {
            Enterprise buyer = enterpriseMapper.selectById(d.getTenantId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("demandId", d.getId());
            row.put("title", d.getTitle());
            row.put("buyerName", buyer == null ? "" : buyer.getName());
            row.put("status", d.getStatus());
            row.put("createdAt", d.getCreatedAt());
            row.put("publishedAt", d.getPublishedAt());
            row.put("startAt", switch (d.getStatus()) {
                case "PENDING_AUDIT" -> d.getCreatedAt();
                case "PUBLISHED" -> d.getPublishedAt();
                case "FACTORY_THINKING" -> d.getFactoryThinkingAt();
                case "BUYER_THINKING" -> d.getBuyerThinkingAt();
                default -> d.getUpdatedAt();
            });
            row.put("endAt", switch (d.getStatus()) {
                case "PUBLISHED" -> d.getIntentionEndAt();
                case "FACTORY_THINKING" -> d.getFactoryThinkingEndAt();
                case "BUYER_THINKING" -> d.getBuyerThinkingEndAt();
                default -> null;
            });
            boolean aiPending = false;
            boolean aiIssued = false;
            if ("SOLUTION_GENERATED".equals(d.getStatus())) {
                Long pending = solutionMapper.selectCount(new LambdaQueryWrapper<Solution>()
                        .eq(Solution::getDemandId, d.getId())
                        .eq(Solution::getStatus, "PENDING_REVIEW"));
                Long active = solutionMapper.selectCount(new LambdaQueryWrapper<Solution>()
                        .eq(Solution::getDemandId, d.getId())
                        .eq(Solution::getStatus, "ACTIVE"));
                aiPending = pending != null && pending > 0;
                aiIssued = active != null && active > 0;
            }
            String action = switch (d.getStatus()) {
                case "PENDING_AUDIT" -> "待审核发布";
                case "PUBLISHED" -> "意向期进行中";
                case "FACTORY_THINKING" -> "工厂思考期，等待填报";
                case "BUYER_THINKING" -> "买家思考期，等待交保证金";
                case "SOLUTION_GENERATED" -> (aiIssued && !aiPending) ? "已下发，等待买家选定方案" : "AI 方案待审核下发";
                default -> "";
            };
            row.put("action", action);
            row.put("needHandle", "PENDING_AUDIT".equals(d.getStatus())
                    || ("SOLUTION_GENERATED".equals(d.getStatus()) && (!aiIssued || aiPending)));
            out.add(row);
        }
        return out;
    }

    private Long countByStatus(String status) {
        return demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, status));
    }
}
