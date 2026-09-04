package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.domain.notify.SiteNotify;
import com.dsh.platform.domain.order.WorkStageSplitter;
import com.dsh.platform.domain.status.DemandStateMachine;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.AttachmentMapper;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SolutionMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractMapper contractMapper;
    private final OrderMapper orderMapper;
    private final DemandMapper demandMapper;
    private final SolutionMapper solutionMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final AttachmentMapper attachmentMapper;
    private final FileService fileService;
    private final WorkStageSplitter workStageSplitter;
    private final DemandStateMachine stateMachine;
    private final SiteNotify siteNotify;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final QuotationMapper quotationMapper;

    public void createDrafts(Long orderId, String comboJson) {
        for (Long factoryId : factoryIdsFromCombo(comboJson)) {
            Long exists = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getOrderId, orderId)
                    .eq(Contract::getTenantId, factoryId));
            if (exists != null && exists > 0) {
                continue;
            }
            Contract c = new Contract();
            c.setOrderId(orderId);
            c.setTenantId(factoryId);
            c.setVersion(1);
            c.setStatus("DRAFT");
            c.setBuyerRead(0);
            c.setFactoryRead(0);
            contractMapper.insert(c);
        }
    }

    public List<Contract> listByOrder(Long orderId) {
        Order o = requireOrder(orderId);
        assertCanViewOrder(o);
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .orderByAsc(Contract::getTenantId));
        if ("FACTORY".equals(UserContext.role())) {
            list = list.stream()
                    .filter(c -> UserContext.tenantId().equals(c.getTenantId()))
                    .toList();
        }
        Solution s = solutionMapper.selectById(o.getSolutionId());
        list.forEach(c -> enrich(c, s));
        return list;
    }

    public Contract getMine(Long orderId) {
        Order o = requireOrder(orderId);
        if ("FACTORY".equals(UserContext.role())) {
            return requireContract(orderId, UserContext.tenantId());
        }
        List<Contract> all = listByOrder(orderId);
        if (all.size() == 1) {
            return all.get(0);
        }
        throw new BizException("请按工厂查看合同列表");
    }

    public List<Contract> listPendingReview() {
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "PENDING_REVIEW")
                .orderByDesc(Contract::getId));
        list.forEach(c -> {
            Order o = orderMapper.selectById(c.getOrderId());
            Solution s = o == null ? null : solutionMapper.selectById(o.getSolutionId());
            enrich(c, s);
        });
        return list;
    }

    /** 合同管理台账：全量合同（含草拟/已签/已审），带需求标题与买家名。 */
    public List<Contract> listAll(String status) {
        LambdaQueryWrapper<Contract> q = new LambdaQueryWrapper<Contract>()
                .orderByDesc(Contract::getId);
        if (status != null && !status.isBlank()) {
            q.eq(Contract::getStatus, status.trim());
        }
        List<Contract> list = contractMapper.selectList(q);
        for (Contract c : list) {
            Order o = orderMapper.selectById(c.getOrderId());
            Solution s = o == null ? null : solutionMapper.selectById(o.getSolutionId());
            enrich(c, s);
            if (o != null) {
                Demand d = demandMapper.selectById(o.getDemandId());
                if (d != null) {
                    c.setDemandTitle(d.getTitle());
                    Enterprise buyer = enterpriseMapper.selectById(d.getTenantId());
                    c.setBuyerName(buyer == null ? "" : buyer.getName());
                }
            }
        }
        return list;
    }

    @Transactional
    public void upload(Long orderId, Long factoryTenantId, Long attachmentId) {
        Order o = requireOrder(orderId);
        assertBuyer(o);
        if ("COMPLETED".equals(o.getStatus())) {
            throw new BizException("订单已完成，不能再改合同");
        }
        Contract c = requireContract(orderId, factoryTenantId);
        if ("SIGNED".equals(c.getStatus())) {
            throw new BizException("该厂合同已审过，不能再改");
        }
        if (attachmentId == null) {
            throw new BizException("请先上传你们拟定的合同文件");
        }
        Long boundId = fileService.bind(attachmentId, "CONTRACT", c.getId());
        c.setAttachmentId(boundId != null ? boundId : attachmentId);
        c.setFactoryRead(0);
        c.setFactorySign(null);
        if (c.getBuyerSign() == null || c.getBuyerSign().isBlank()) {
            c.setBuyerRead(0);
        }
        c.setStatus("DRAFT");
        markPendingIfReady(c);
        contractMapper.updateById(c);
        touchFactoryQuotes(o.getDemandId(), factoryTenantId);
    }

    @Transactional
    public void buyerSign(Long orderId, Long factoryTenantId, boolean read, String sign) {
        if (factoryTenantId == null) {
            buyerSignAll(orderId, read, sign);
            return;
        }
        Order o = requireOrder(orderId);
        assertBuyer(o);
        applyBuyerSign(requireContract(orderId, factoryTenantId), read, sign);
    }

    @Transactional
    public void buyerSignAll(Long orderId, boolean read, String sign) {
        Order o = requireOrder(orderId);
        assertBuyer(o);
        if (!read) {
            throw new BizException("请先勾选已阅读合同");
        }
        if (sign == null || sign.isBlank()) {
            throw new BizException("请完成手写签名");
        }
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .orderByAsc(Contract::getTenantId));
        if (list.isEmpty()) {
            throw new BizException("没有待签合同");
        }
        for (Contract c : list) {
            if ("SIGNED".equals(c.getStatus())) {
                continue;
            }
            if (c.getAttachmentId() == null) {
                Enterprise e = enterpriseMapper.selectById(c.getTenantId());
                String name = e == null || e.getName() == null ? ("工厂" + c.getTenantId()) : e.getName();
                throw new BizException("请先分别上传「" + name + "」的合同文件，再统一签名");
            }
        }
        for (Contract c : list) {
            if ("SIGNED".equals(c.getStatus())) {
                continue;
            }
            applyBuyerSign(c, true, sign);
        }
    }

    private void applyBuyerSign(Contract c, boolean read, String sign) {
        if (!read) {
            throw new BizException("请先勾选已阅读合同");
        }
        if (sign == null || sign.isBlank()) {
            throw new BizException("请完成手写签名");
        }
        if (c.getAttachmentId() == null) {
            throw new BizException("请先上传该厂的合同文件");
        }
        if ("SIGNED".equals(c.getStatus())) {
            throw new BizException("该厂合同已审过");
        }
        c.setBuyerRead(1);
        c.setBuyerSign(sign);
        markPendingIfReady(c);
        contractMapper.updateById(c);
        Order o = orderMapper.selectById(c.getOrderId());
        if (o != null) {
            touchFactoryQuotes(o.getDemandId(), c.getTenantId());
        }
    }

    @Transactional
    public void factorySign(Long orderId, boolean read, String sign) {
        requireOrder(orderId);
        if (!"FACTORY".equals(UserContext.role())) {
            throw new BizException(403, "仅中标工厂可签");
        }
        if (!read) {
            throw new BizException("请先勾选已阅读合同");
        }
        if (sign == null || sign.isBlank()) {
            throw new BizException("请完成手写签名");
        }
        Contract c = requireContract(orderId, UserContext.tenantId());
        if (c.getAttachmentId() == null) {
            throw new BizException("买家尚未上传与你的合同");
        }
        if ("SIGNED".equals(c.getStatus())) {
            throw new BizException("合同已审过");
        }
        c.setFactoryRead(1);
        c.setFactorySign(sign);
        markPendingIfReady(c);
        contractMapper.updateById(c);
        Order o = orderMapper.selectById(orderId);
        if (o != null) {
            touchFactoryQuotes(o.getDemandId(), UserContext.tenantId());
        }
    }

    @Transactional
    public void approve(Long orderId, Long factoryTenantId) {
        Order o = requireOrder(orderId);
        if ("COMPLETED".equals(o.getStatus())) {
            throw new BizException("订单已完成");
        }
        Contract c = requireContract(orderId, factoryTenantId);
        if (c.getAttachmentId() == null
                || c.getBuyerRead() == null || c.getBuyerRead() != 1
                || c.getBuyerSign() == null || c.getBuyerSign().isBlank()
                || c.getFactoryRead() == null || c.getFactoryRead() != 1
                || c.getFactorySign() == null || c.getFactorySign().isBlank()) {
            throw new BizException("该厂合同双方尚未完成阅读和签名");
        }
        Solution s = solutionMapper.selectById(o.getSolutionId());
        workStageSplitter.splitForFactory(o, s, factoryTenantId);
        c.setStatus("SIGNED");
        c.setSignedAt(LocalDateTime.now());
        contractMapper.updateById(c);
        touchFactoryQuotes(o.getDemandId(), factoryTenantId);
        if ("CREATED".equals(o.getStatus())) {
            o.setStatus("IN_PRODUCTION");
            orderMapper.updateById(o);
        }
        Demand d = demandMapper.selectById(o.getDemandId());
        DemandStatus st = DemandStatus.of(d.getStatus());
        if (st == DemandStatus.SOLUTION_SELECTED) {
            stateMachine.transit(d, DemandStatus.CONTRACTED);
            demandMapper.updateById(d);
            stateMachine.transit(d, DemandStatus.IN_PRODUCTION);
            demandMapper.updateById(d);
        } else if (st == DemandStatus.CONTRACTED) {
            stateMachine.transit(d, DemandStatus.IN_PRODUCTION);
            demandMapper.updateById(d);
        }
        siteNotify.send(d.getTenantId(), "合同已审过#" + orderId + "-" + factoryTenantId,
                "与该厂的合同已确认签署，该厂可以交付。");
        siteNotify.send(factoryTenantId, "合同已审过#" + orderId, "平台已确认你这份合同已签署，请按工单交付。");
        auditService.record("合同审核通过", "CONTRACT", c.getId(),
                "订单#" + orderId + " 工厂#" + factoryTenantId);
    }

    public boolean isSigned(Long orderId, Long factoryTenantId) {
        Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .eq(Contract::getTenantId, factoryTenantId)
                .last("limit 1"));
        return c != null && "SIGNED".equals(c.getStatus());
    }

    public boolean allSigned(Long orderId) {
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId));
        if (list.isEmpty()) {
            return false;
        }
        return list.stream().allMatch(c -> "SIGNED".equals(c.getStatus()));
    }

    private void touchFactoryQuotes(Long demandId, Long factoryTenantId) {
        if (demandId == null || factoryTenantId == null) {
            return;
        }
        quotationMapper.update(null, new LambdaUpdateWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, factoryTenantId)
                .set(Quotation::getUpdatedAt, LocalDateTime.now()));
    }

    private void markPendingIfReady(Contract c) {
        if (c.getBuyerRead() != null && c.getBuyerRead() == 1
                && c.getBuyerSign() != null && !c.getBuyerSign().isBlank()
                && c.getFactoryRead() != null && c.getFactoryRead() == 1
                && c.getFactorySign() != null && !c.getFactorySign().isBlank()) {
            c.setStatus("PENDING_REVIEW");
        }
    }

    private void enrich(Contract c, Solution s) {
        Enterprise e = enterpriseMapper.selectById(c.getTenantId());
        c.setFactoryName(e == null ? ("厂" + c.getTenantId()) : e.getName());
        Order o = c.getOrderId() == null ? null : orderMapper.selectById(c.getOrderId());
        if (o != null && c.getTenantId() != null) {
            Integer min = null;
            Integer max = null;
            List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                    .eq(Quotation::getDemandId, o.getDemandId())
                    .eq(Quotation::getTenantId, c.getTenantId()));
            for (Quotation q : qs) {
                if (q.getMinQty() != null) {
                    min = min == null ? q.getMinQty() : Math.min(min, q.getMinQty());
                }
                if (q.getMaxQty() != null) {
                    max = max == null ? q.getMaxQty() : Math.max(max, q.getMaxQty());
                }
            }
            c.setMinQty(min);
            c.setMaxQty(max);
        }
        if (s == null || c.getTenantId() == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> item : readCombo(s.getFinalComboJson())) {
            if (c.getTenantId().equals(asLong(item.get("factoryId")))) {
                Object n = item.get("processName");
                names.add(n == null ? "工序" : n.toString());
            }
        }
        c.setProcessNames(String.join("、", names));
        if (c.getAttachmentId() != null) {
            Attachment a = attachmentMapper.selectById(c.getAttachmentId());
            c.setFileName(a == null ? null : a.getFileName());
        }
        c.setSignHint(signHint(c));
    }

    private String signHint(Contract c) {
        List<String> parts = new ArrayList<>();
        parts.add(c.getBuyerSign() != null && !c.getBuyerSign().isBlank() ? "买家已签" : "买家未签");
        parts.add(c.getFactorySign() != null && !c.getFactorySign().isBlank() ? "工厂已签" : "工厂未签");
        if ("SIGNED".equals(c.getStatus())) {
            parts.add("运营已确认");
        } else if ("PENDING_REVIEW".equals(c.getStatus())) {
            parts.add("待运营确认");
        }
        return String.join(" · ", parts);
    }

    private Set<Long> factoryIdsFromCombo(String json) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Map<String, Object> item : readCombo(json)) {
            Long id = asLong(item.get("factoryId"));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void assertBuyer(Order o) {
        Demand d = demandMapper.selectById(o.getDemandId());
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException(403, "无权操作该订单合同");
        }
    }

    private void assertCanViewOrder(Order o) {
        String role = UserContext.role();
        if ("BUYER".equals(role)) {
            assertBuyer(o);
            return;
        }
        if ("FACTORY".equals(role)) {
            Contract mine = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getOrderId, o.getId())
                    .eq(Contract::getTenantId, UserContext.tenantId())
                    .last("limit 1"));
            if (mine == null) {
                throw new BizException(403, "无权查看该合同");
            }
            return;
        }
        if (!"OPERATOR".equals(role) && !"SUPER_ADMIN".equals(role) && !"INSPECTION".equals(role)) {
            throw new BizException(403, "无权查看该合同");
        }
    }

    private Order requireOrder(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在");
        }
        return o;
    }

    private Contract requireContract(Long orderId, Long factoryTenantId) {
        if (factoryTenantId == null) {
            throw new BizException("请指定工厂");
        }
        Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .eq(Contract::getTenantId, factoryTenantId)
                .last("limit 1"));
        if (c == null) {
            throw new BizException("该厂没有合同");
        }
        if ("FACTORY".equals(UserContext.role()) && !UserContext.tenantId().equals(c.getTenantId())) {
            throw new BizException(403, "只能看自己的合同");
        }
        Order o = requireOrder(orderId);
        if ("BUYER".equals(UserContext.role())) {
            assertBuyer(o);
        }
        Solution s = solutionMapper.selectById(o.getSolutionId());
        enrich(c, s);
        return c;
    }

    private List<Map<String, Object>> readCombo(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Long asLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }
}
