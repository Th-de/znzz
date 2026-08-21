package com.dsh.platform.service;

import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CapabilityService {

    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getMine() {
        Enterprise e = enterpriseMapper.selectById(UserContext.tenantId());
        if (e == null) throw new BizException("企业不存在");
        JsonNode node = read(e.getCapabilityJson());
        return Map.of(
                "complete", isComplete(node),
                "capability", node == null || node.isNull() ? Map.of() : objectMapper.convertValue(node, Map.class)
        );
    }

    public void save(Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            JsonNode node = objectMapper.readTree(json);
            if (!isComplete(node)) {
                throw new BizException("请补全设备、材料、工艺、合格率和至少一条工序产能");
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
        return node;
    }

    public static boolean isComplete(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (!hasItems(node.get("devices")) || blankName(node.get("devices"))) return false;
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

    private static boolean blankName(JsonNode devices) {
        for (JsonNode d : devices) {
            if (d.path("name").asText("").isBlank()) return true;
        }
        return false;
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
