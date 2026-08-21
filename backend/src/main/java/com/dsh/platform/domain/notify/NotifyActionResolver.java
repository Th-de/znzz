package com.dsh.platform.domain.notify;

import com.dsh.platform.dto.NotifyView;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.mapper.WorkStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotifyActionResolver {

    private final WorkStageMapper workStageMapper;

    public NotifyView view(Notify n, String role) {
        NotifyView v = new NotifyView();
        v.setId(n.getId());
        v.setTitle(n.getTitle());
        v.setContent(n.getContent());
        v.setIsRead(n.getIsRead());
        v.setCreatedAt(n.getCreatedAt());
        fill(v, n.getTitle(), role);
        return v;
    }

    private void fill(NotifyView v, String title, String role) {
        if (title == null) {
            return;
        }
        boolean factory = "FACTORY".equals(role);
        Long hashId = trailingId(title);
        if (title.startsWith("请按厂上传合同#") || title.startsWith("请上传合同#") || title.startsWith("合同已审过#")) {
            Long orderId = firstId(hashId);
            v.setLink("/buyer/order/" + orderId);
            v.setAction(title.contains("上传") ? "去上传合同" : "查看订单");
            if (factory) {
                v.setLink("/factory/stages");
                v.setAction("去工单");
            }
            return;
        }
        if (title.startsWith("请签合同#")) {
            v.setLink("/factory/stages");
            v.setAction("去签合同");
            return;
        }
        if (title.startsWith("请付阶段款#")) {
            v.setLink(buyerOrderByStage(hashId));
            v.setAction("去支付");
            return;
        }
        if (title.startsWith("阶段不合格#") || title.startsWith("阶段问卷#")) {
            if (factory) {
                v.setLink("/factory/stages");
                v.setAction("去填问卷");
            } else {
                v.setLink(buyerOrderByStage(hashId));
                v.setAction(title.startsWith("阶段问卷") ? "去填问卷" : "查看工单");
            }
            return;
        }
        if (title.startsWith("订单已完成#")) {
            v.setLink("/buyer/order/" + hashId);
            v.setAction("查看订单");
            return;
        }
        if (title.startsWith("订单已结算#")) {
            v.setLink("/factory/stages");
            v.setAction("查看工单");
            return;
        }
        if (title.startsWith("工单逾期#")) {
            if (factory) {
                v.setLink("/factory/stages");
                v.setAction("去工单");
            } else {
                v.setLink(buyerOrderByStage(hashId));
                v.setAction("查看订单");
            }
            return;
        }
        if (title.startsWith("运营下发了AI方案#") || title.startsWith("方案已生成#")) {
            v.setLink(hashId != null ? "/buyer/solutions/" + hashId : "/buyer/home");
            v.setAction("去看方案");
            return;
        }
        if (title.startsWith("保证金期已开启") || "竞标提醒".equals(title)) {
            v.setLink("/factory/demands");
            v.setAction(title.startsWith("保证金") ? "去锁定报价" : "去看需求");
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

    private String buyerOrderByStage(Long stageId) {
        if (stageId == null) {
            return "/buyer/orders";
        }
        WorkStage ws = workStageMapper.selectById(stageId);
        if (ws == null || ws.getOrderId() == null) {
            return "/buyer/orders";
        }
        return "/buyer/order/" + ws.getOrderId();
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
