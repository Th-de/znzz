package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.AuditLog;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.AuditLogMapper;
import com.dsh.platform.mapper.SysUserMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作留痕：买家、工厂、运营的关键动作调用 record()，运营端「操作日志」页可查。
 * 记录失败不阻断业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public void record(String action, String targetType, Long targetId, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.setActorId(UserContext.userId() == null ? 0L : UserContext.userId());
            a.setAction(action);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setAfterJson(toDetailJson(detail));
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
            a.setActorName(names.computeIfAbsent(a.getActorId(), this::actorDisplayName));
            a.setTargetLabel(targetLabelOf(a.getTargetType()));
            a.setAfterJson(extractDetail(a.getAfterJson()));
        }
        return list;
    }

    private String actorDisplayName(Long id) {
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            return "用户" + id;
        }
        String name = u.getRealName() == null || u.getRealName().isBlank() ? u.getPhone() : u.getRealName();
        String prefix = rolePrefix(u.getRole());
        return prefix == null ? name : prefix + " · " + name;
    }

    private static String rolePrefix(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "BUYER" -> "买家";
            case "FACTORY" -> "工厂";
            case "OPERATOR" -> "运营";
            case "SUPER_ADMIN" -> "超管";
            case "INSPECTOR", "INSPECTION" -> "质检";
            default -> null;
        };
    }

    static String targetLabelOf(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "DEMAND" -> "需求";
            case "ORDER" -> "订单";
            case "SOLUTION" -> "方案";
            case "STAGE" -> "工单";
            case "CONTRACT" -> "合同";
            default -> type;
        };
    }

    String toDetailJson(String detail) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("detail", detail == null ? "" : detail));
        } catch (Exception e) {
            return detail;
        }
    }

    String extractDetail(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("{")) {
            try {
                JsonNode n = objectMapper.readTree(s);
                if (n != null && n.has("detail") && !n.get("detail").isNull()) {
                    return n.get("detail").asText();
                }
            } catch (Exception ignored) {
                // 非 JSON 则原样返回
            }
        }
        return s;
    }
}
