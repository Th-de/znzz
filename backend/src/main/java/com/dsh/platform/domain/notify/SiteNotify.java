package com.dsh.platform.domain.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.NotifyMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SiteNotify {

    private final NotifyMapper notifyMapper;
    private final SysUserMapper sysUserMapper;
    private final QuotationMapper quotationMapper;
    private final NotifyActionResolver notifyActionResolver;

    public void send(Long tenantId, String title, String content) {
        if (tenantId == null) {
            return;
        }
        Long demandId = notifyActionResolver.resolveDemandId(title);
        if (blockedAfterLose(tenantId, demandId, title)) {
            return;
        }
        Notify n = new Notify();
        n.setTenantId(tenantId);
        n.setDemandId(demandId);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        notifyMapper.insert(n);
    }

    /** 工厂落选后不再接收该需求后续通知；「已落选」本身仍写入。 */
    private boolean blockedAfterLose(Long tenantId, Long demandId, String title) {
        if (demandId == null) {
            return false;
        }
        if (title != null && title.startsWith("已落选#")) {
            return false;
        }
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getTenantId, tenantId)
                .eq(Quotation::getDemandId, demandId));
        if (qs == null || qs.isEmpty()) {
            return false;
        }
        boolean won = false;
        boolean lost = false;
        for (Quotation q : qs) {
            if ("WIN".equals(q.getStatus())) {
                won = true;
            }
            if ("LOSE".equals(q.getStatus())) {
                lost = true;
            }
        }
        return lost && !won;
    }

    public void notifyOperators(String title, String content) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, "OPERATOR", "SUPER_ADMIN")
                .eq(SysUser::getStatus, "ENABLED"));
        Set<Long> tenants = new LinkedHashSet<>();
        for (SysUser u : users) {
            if (u.getTenantId() != null) {
                tenants.add(u.getTenantId());
            }
        }
        for (Long tid : tenants) {
            send(tid, title, content);
        }
    }

    public boolean exists(Long tenantId, String title) {
        return notifyMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notify>()
                .eq(Notify::getTenantId, tenantId)
                .eq(Notify::getTitle, title)) > 0;
    }
}
