package com.dsh.platform.domain.notify;

import com.dsh.platform.entity.Notify;
import com.dsh.platform.mapper.NotifyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SiteNotify {

    private final NotifyMapper notifyMapper;

    public void send(Long tenantId, String title, String content) {
        if (tenantId == null) {
            return;
        }
        Notify n = new Notify();
        n.setTenantId(tenantId);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        notifyMapper.insert(n);
    }

    public boolean exists(Long tenantId, String title) {
        return notifyMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notify>()
                .eq(Notify::getTenantId, tenantId)
                .eq(Notify::getTitle, title)) > 0;
    }
}
