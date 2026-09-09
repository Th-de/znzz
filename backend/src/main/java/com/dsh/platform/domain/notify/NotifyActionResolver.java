package com.dsh.platform.domain.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.domain.status.DemandStatus;
import com.dsh.platform.dto.NotifyView;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotifyActionResolver {

    private final WorkStageMapper workStageMapper;
    private final OrderMapper orderMapper;
    private final DemandMapper demandMapper;
    private final ContractMapper contractMapper;

    public NotifyView view(Notify n, String role) {
        return view(n, role, null);
    }

    public NotifyView view(Notify n, String role, Demand demand) {
        NotifyView v = new NotifyView();
        v.setId(n.getId());
        v.setDemandId(n.getDemandId() != null ? n.getDemandId() : resolveDemandId(n.getTitle()));
        v.setTitle(n.getTitle());
        v.setContent(n.getContent());
        v.setIsRead(n.getIsRead());
        v.setCreatedAt(n.getCreatedAt());
        fill(v, n.getTitle(), role);
        if (demand == null && v.getDemandId() != null) {
            demand = demandMapper.selectById(v.getDemandId());
        }
        dropStaleAction(v, n.getTitle(), demand);
        return v;
    }

    private void fill(NotifyView v, String title, String role) {
        if (title == null) {
            return;
        }
        boolean factory = "FACTORY".equals(role);
        Long hashId = trailingId(title);
        if (title.startsWith("请按厂上传合同#") || title.startsWith("请上传合同#")
                || title.startsWith("已确认签署并派单#") || title.startsWith("合同已审过#")) {
            Long orderId = firstId(hashId);
            v.setLink(buyerDemandByOrder(orderId));
            v.setAction(title.contains("上传") ? "去上传合同" : "查看需求");
            if (factory) {
                v.setLink(factoryBidLink(v.getDemandId()));
                v.setAction(title.startsWith("已确认签署并派单#") || title.startsWith("买家已确认合同#") ? "去生产" : "查看报名");
            }
            return;
        }
        if (title.startsWith("工厂已签署合同#")) {
            v.setLink(hashId != null ? "/buyer/demand/" + hashId : "/buyer/home");
            v.setAction("去确认");
            return;
        }
        if (title.startsWith("待下发合同#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("查看报名");
            return;
        }
        if (title.startsWith("买家已确认合同#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("去生产");
            return;
        }
        if (title.startsWith("请签合同#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("去签署");
            return;
        }
        if (title.startsWith("请付阶段款#")) {
            v.setLink(buyerOrderByStage(hashId));
            v.setAction("去支付");
            return;
        }
        if (title.startsWith("请处理质检结果#")) {
            v.setLink(buyerOrderByStage(hashId));
            v.setAction("去处理");
            return;
        }
        if (title.startsWith("阶段不合格#") || title.startsWith("阶段问卷#")) {
            if (factory) {
                v.setLink(factoryBidLink(v.getDemandId()));
                v.setAction("去填问卷");
            } else {
                v.setLink(buyerOrderByStage(hashId));
                v.setAction(title.startsWith("阶段问卷") ? "去填问卷" : "查看工单");
            }
            return;
        }
        if (title.startsWith("订单已完成#")) {
            v.setLink(buyerDemandByOrder(hashId));
            v.setAction("查看需求");
            return;
        }
        if (title.startsWith("订单已结算#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("查看生产");
            return;
        }
        if (title.startsWith("工单逾期#")) {
            if (factory) {
                v.setLink(factoryBidLink(v.getDemandId()));
                v.setAction("去生产");
            } else {
                v.setLink(buyerOrderByStage(hashId));
                v.setAction("查看订单");
            }
            return;
        }
        if (title.startsWith("运营下发了AI方案#") || title.startsWith("方案已生成#")) {
            v.setLink(hashId != null ? "/buyer/demand/" + hashId : "/buyer/home");
            v.setAction("去看方案");
            return;
        }
        if (title.startsWith("方案已确认#") || title.startsWith("买家已确认方案#")
                || title.startsWith("AI方案待审核#") || title.startsWith("AI方案生成失败#")) {
            v.setLink(hashId != null ? "/buyer/demand/" + hashId : "/buyer/home");
            v.setAction("查看需求");
            return;
        }
        if (title.startsWith("保证金期已开启") || title.startsWith("锁价截止提醒#") || "竞标提醒".equals(title)) {
            v.setLink("/factory/demands");
            v.setAction(title.contains("锁价") || title.startsWith("保证金") ? "去锁定报价" : "去看需求");
            return;
        }
        if (title.startsWith("未锁价扣除意向金#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("查看报名");
            return;
        }
        if (title.startsWith("已落选#")) {
            v.setLink(factoryBidLink(v.getDemandId()));
            v.setAction("查看报名");
            return;
        }
        if (title.startsWith("报名成功#") || title.startsWith("已取消报名#")
                || title.startsWith("报价已提交#") || title.startsWith("已取消报价#")
                || title.startsWith("已锁定报价#") || title.startsWith("已取消锁定报价#")
                || title.startsWith("买家取消需求#") || title.startsWith("买家已确认继续#")
                || title.startsWith("买家申请取消需求#") || title.startsWith("工厂思考期开始")
                || title.startsWith("思考期超时扣分#") || title.startsWith("订单已结束#")
                || title.startsWith("需求流拍#")) {
            if (factory) {
                v.setLink(factoryBidLink(v.getDemandId()));
                v.setAction(title.startsWith("工厂思考期开始") ? "去填报报价" : "查看报名");
            } else {
                v.setLink(hashId != null ? "/buyer/demand/" + hashId : "/buyer/home");
                v.setAction("查看需求");
            }
            return;
        }
        if (title.startsWith("工厂已报名#") || title.startsWith("工厂取消报名#")
                || title.startsWith("工厂已填报#") || title.startsWith("有工厂退出#")
                || title.startsWith("工厂已锁定报价#") || title.startsWith("工厂取消锁定报价#")
                || title.startsWith("请决定是否继续#") || title.startsWith("保证金已冻结#")) {
            v.setLink(hashId != null ? "/buyer/demand/" + hashId : "/buyer/home");
            v.setAction(title.startsWith("请决定") ? "去决定" : "查看需求");
            return;
        }
        if (title.startsWith("覆盖度更新#") || title.startsWith("意向期末日提醒#")
                || title.startsWith("意向期结束#") || title.startsWith("取消进入审核#")
                || title.startsWith("取消未通过#") || title.startsWith("需求流拍#")
                || title.startsWith("需求已取消#")) {
            v.setLink("/buyer/demand/" + hashId);
            v.setAction(title.startsWith("意向期结束") ? "去决定" : "查看需求");
            return;
        }
        if ("需求已取消".equals(title)) {
            v.setLink("/buyer/home");
            v.setAction("返回首页");
        }
    }

    /** 阶段已过、操作做完则不再给出「去××」按钮，查看类入口保留。 */
    private void dropStaleAction(NotifyView v, String title, Demand demand) {
        String action = v.getAction();
        if (action == null || !isTaskAction(action)) {
            return;
        }
        DemandStatus st = demand == null ? null : DemandStatus.of(demand.getStatus());
        if (!actionOpen(action, title, st)) {
            v.setAction(null);
            v.setLink(null);
        }
    }

    private static boolean isTaskAction(String action) {
        return "去填报报价".equals(action)
                || "去锁定报价".equals(action)
                || "去决定".equals(action)
                || "去看方案".equals(action)
                || "去上传合同".equals(action)
                || "去签署".equals(action)
                || "去支付".equals(action)
                || "去处理".equals(action)
                || "去填问卷".equals(action)
                || "去看需求".equals(action)
                || "去确认".equals(action)
                || "去办理".equals(action);
    }

    private boolean actionOpen(String action, String title, DemandStatus st) {
        if (st == DemandStatus.CANCELLED || st == DemandStatus.FLOW_FAILED) {
            return false;
        }
        return switch (action) {
            case "去填报报价" -> st == DemandStatus.FACTORY_THINKING;
            case "去锁定报价" -> st == DemandStatus.LOCKING;
            case "去决定" -> st == DemandStatus.BUYER_THINKING || st == DemandStatus.THINKING;
            case "去看方案" -> st == DemandStatus.SOLUTION_GENERATED;
            case "去上传合同" -> st == DemandStatus.SOLUTION_SELECTED;
            case "去签署" -> canSign(title, st);
            case "去确认" -> st == DemandStatus.SOLUTION_SELECTED;
            case "去支付" -> canPay(title);
            case "去处理" -> canDecideInspection(title);
            case "去填问卷" -> canSurvey(title);
            case "去看需求" -> st == DemandStatus.PUBLISHED;
            default -> true;
        };
    }

    private boolean canSign(String title, DemandStatus st) {
        if (st == DemandStatus.IN_PRODUCTION || st == DemandStatus.COMPLETED
                || st == DemandStatus.CONTRACTED) {
            return false;
        }
        if (st != DemandStatus.SOLUTION_SELECTED) {
            return false;
        }
        Long orderId = trailingId(title);
        if (orderId == null) {
            return true;
        }
        Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrderId, orderId)
                .eq(Contract::getTenantId, UserContext.tenantId())
                .last("limit 1"));
        return c == null || !StringUtils.hasText(c.getFactorySign());
    }

    private boolean canPay(String title) {
        Long stageId = trailingId(title);
        if (stageId == null) {
            return false;
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            return false;
        }
        String escrow = ws.getEscrowStatus();
        return "PENDING_PAY".equals(escrow);
    }

    private boolean canDecideInspection(String title) {
        Long stageId = trailingId(title);
        if (stageId == null) {
            return false;
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        return ws != null && "FAIL".equals(ws.getStatus());
    }

    private boolean canSurvey(String title) {
        Long stageId = trailingId(title);
        if (stageId == null) {
            return false;
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null) {
            return false;
        }
        String status = ws.getStatus();
        return !"CLOSED".equals(status) && !"CANCELLED".equals(status)
                && !"SETTLED".equals(ws.getEscrowStatus());
    }

    public Long resolveDemandId(String title) {
        Long id = trailingId(title);
        if (id == null) {
            return null;
        }
        if (isStageTitle(title)) {
            WorkStage ws = workStageMapper.selectById(id);
            if (ws == null || ws.getOrderId() == null) {
                return null;
            }
            Order o = orderMapper.selectById(ws.getOrderId());
            return o == null ? null : o.getDemandId();
        }
        if (isOrderTitle(title)) {
            Order o = orderMapper.selectById(id);
            return o == null ? null : o.getDemandId();
        }
        return id;
    }

    private static boolean isStageTitle(String title) {
        return title.startsWith("请付阶段款#")
                || title.startsWith("阶段问卷#")
                || title.startsWith("阶段不合格#")
                || title.startsWith("请处理质检结果#")
                || title.startsWith("质检已提交#")
                || title.startsWith("质检不合格#")
                || title.startsWith("让步已确认#")
                || title.startsWith("买家已让步#")
                || title.startsWith("请返工#")
                || title.startsWith("已要求返工#")
                || title.startsWith("请付质检费#")
                || title.startsWith("请补件#")
                || title.startsWith("数量返工#")
                || title.startsWith("本段已关闭#")
                || title.startsWith("已关闭工单#")
                || title.startsWith("保证金已抵扣尾款#")
                || title.startsWith("工单逾期#");
    }

    private static boolean isOrderTitle(String title) {
        return title.startsWith("请按厂上传合同#")
                || title.startsWith("请上传合同#")
                || title.startsWith("合同已审过#")
                || title.startsWith("待下发合同#")
                || title.startsWith("请签合同#")
                || title.startsWith("合同签署期开始#")
                || title.startsWith("已确认签署并派单#")
                || title.startsWith("买家已确认合同#")
                || title.startsWith("订单已完成#")
                || title.startsWith("订单已结算#")
                || title.startsWith("订单已取消#")
                || title.startsWith("买家违约赔偿#")
                || title.startsWith("工厂未签已获赔偿#")
                || title.startsWith("合同取消已扣保证金#")
                || title.startsWith("保证金已退还#")
                || title.startsWith("下一期已开启#")
                || title.startsWith("工厂进入下一期#");
    }

    private String factoryBidLink(Long demandId) {
        return demandId == null ? "/factory/quotations" : "/factory/quotations/" + demandId;
    }

    private String buyerOrderByStage(Long stageId) {
        if (stageId == null) {
            return "/buyer/home";
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || ws.getOrderId() == null) {
            return "/buyer/home";
        }
        return buyerDemandByOrder(ws.getOrderId());
    }

    private String buyerDemandByOrder(Long orderId) {
        if (orderId == null) {
            return "/buyer/home";
        }
        Order o = orderMapper.selectById(orderId);
        if (o == null || o.getDemandId() == null) {
            return "/buyer/home";
        }
        return "/buyer/demand/" + o.getDemandId();
    }

    private static Long firstId(Long raw) {
        return raw;
    }

    private static Long trailingId(String title) {
        int i = title.lastIndexOf('#');
        if (i < 0 || i == title.length() - 1) {
            return null;
        }
        String tail = title.substring(i + 1);
        int dash = tail.indexOf('-');
        if (dash > 0) {
            tail = tail.substring(0, dash);
        }
        try {
            return Long.valueOf(tail);
        } catch (Exception e) {
            return null;
        }
    }
}
