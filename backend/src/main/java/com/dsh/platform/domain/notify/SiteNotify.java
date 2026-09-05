package com.dsh.platform.domain.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.NotifyMapper;
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
    private final NotifyActionResolver notifyActionResolver;

    public void send(Long tenantId, String title, String content) {
        if (tenantId == null) {
            return;
        }
        Notify n = new Notify();
        n.setTenantId(tenantId);
        n.setDemandId(notifyActionResolver.resolveDemandId(title));
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        notifyMapper.insert(n);
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
