package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.AuditLog;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.AuditLogMapper;
import com.dsh.platform.mapper.SysUserMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营操作留痕：关键操作调用 record()，运营端「操作日志」页可查。
 * 记录失败不阻断业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper sysUserMapper;

    public void record(String action, String targetType, Long targetId, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.setActorId(UserContext.userId() == null ? 0L : UserContext.userId());
            a.setAction(action);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setAfterJson(detail == null ? null
                    : "{\"detail\":\"" + detail.replace("\"", "'") + "\"}");
            auditLogMapper.insert(a);
        } catch (Exception e) {
            log.warn("审计日志记录失败 action={}: {}", action, e.getMessage());
        }
    }

    public List<AuditLog> list(String action, Integer limit) {
        LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId)
                .last("limit " + (limit == null || limit <= 0 || limit > 500 ? 200 : limit));
        if (action != null && !action.isBlank()) {
            q.like(AuditLog::getAction, action.trim());
        }
        List<AuditLog> list = auditLogMapper.selectList(q);
        Map<Long, String> names = new HashMap<>();
        for (AuditLog a : list) {
            a.setActorName(names.computeIfAbsent(a.getActorId(), id -> {
                SysUser u = sysUserMapper.selectById(id);
                return u == null ? ("用户" + id) : (u.getRealName() == null ? u.getPhone() : u.getRealName());
            }));
        }
        return list;
    }
}
