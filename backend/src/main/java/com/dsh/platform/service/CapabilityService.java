package com.dsh.platform.service;

import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Account;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CapabilityService {

    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final AccountService accountService;

    public Map<String, Object> getMine() {
        Enterprise e = enterpriseMapper.selectById(UserContext.tenantId());
        if (e == null) throw new BizException("企业不存在");
        JsonNode node = read(e.getCapabilityJson());
        long deviceCount = deviceService.countMine();
        Map<String, Object> capability = node == null || node.isNull()
                ? Map.of()
                : objectMapper.convertValue(node, Map.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("complete", isComplete(node) && deviceCount > 0);
        out.put("deviceCount", deviceCount);
        out.put("capability", capability);
        // 工序产能由设备推导，只读展示
        out.put("deviceCapacity", deviceService.capacityGroups(UserContext.tenantId()));
        return out;
    }

    public Map<String, Object> mineProfile() {
        Enterprise e = enterpriseMapper.selectById(UserContext.tenantId());
        if (e == null) throw new BizException("企业不存在");
        Account a = accountService.get(e.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("type", e.getType());
        m.put("name", e.getName());
        m.put("creditCode", e.getCreditCode());
        m.put("creditScore", e.getCreditScore());
        m.put("authStatus", e.getAuthStatus());
        m.put("contactName", e.getContactName());
        m.put("address", e.getAddress());
        m.put("balance", a.getBalance());
        m.put("frozen", a.getFrozen());
        m.put("introduction", e.getIntroduction());
        return m;
    }

    public void save(Map<String, Object> body) {
        try {
            // 工序产能不再手填：由设备的适用工序+日产能自动推导后落库
            var groups = deviceService.capacityGroups(UserContext.tenantId());
            if (!groups.isEmpty()) {
                body = new LinkedHashMap<>(body);
                body.put("capacityByProcess", groups);
            }
            String json = objectMapper.writeValueAsString(body);
            JsonNode node = objectMapper.readTree(json);
            if (!isComplete(node)) {
                throw new BizException("请补全材料、工艺、合格率和至少一条工序产能");
            }
            if (deviceService.countMine() <= 0) {
                throw new BizException("请先在「我的设备」中至少添加一台设备");
            }
            Enterprise e = enterpriseMapper.selectById(UserContext.tenantId());
            if (e == null) throw new BizException("企业不存在");
            e.setCapabilityJson(json);
            enterpriseMapper.updateById(e);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BizException("能力档案格式不正确");
        }
    }

    public JsonNode requireComplete(Long tenantId) {
        Enterprise e = enterpriseMapper.selectById(tenantId);
        if (e == null) throw new BizException("企业不存在");
        JsonNode node = read(e.getCapabilityJson());
        if (!isComplete(node)) {
            throw new BizException("请先完善能力档案后再报名");
        }
        long deviceCount = deviceService.countMine();
        if (UserContext.tenantId() != null && UserContext.tenantId().equals(tenantId) && deviceCount <= 0) {
            throw new BizException("请先在「我的设备」中至少添加一台设备后再报名");
        }
        return node;
    }

    public static boolean isComplete(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (!hasItems(node.get("materials"))) return false;
        if (!hasItems(node.get("processes"))) return false;
        if (node.get("yieldRate") == null || node.get("yieldRate").isNull()) return false;
        JsonNode caps = node.get("capacityByProcess");
        if (!hasItems(caps)) return false;
        for (JsonNode c : caps) {
            if (c.path("dailyCapacity").asInt(0) <= 0) return false;
        }
        return true;
    }

    private static boolean hasItems(JsonNode arr) {
        return arr != null && arr.isArray() && !arr.isEmpty();
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
