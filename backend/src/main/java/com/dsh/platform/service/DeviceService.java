package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.mapper.DeviceMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ObjectMapper objectMapper;

    public List<Device> listMine() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, UserContext.tenantId())
                .orderByDesc(Device::getId));
    }

    public long countMine() {
        return deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, UserContext.tenantId()));
    }

    @Transactional
    public Long save(Device req) {
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException("请填写设备名");
        }
        Long tid = UserContext.tenantId();
        if (req.getId() == null) {
            Device d = new Device();
            d.setTenantId(tid);
            fill(d, req);
            if (!StringUtils.hasText(d.getStatus())) {
                d.setStatus("GOOD");
            } else {
                d.setStatus(normalizeStatus(d.getStatus()));
            }
            deviceMapper.insert(d);
            return d.getId();
        }
        Device d = requireMine(req.getId());
        fill(d, req);
        deviceMapper.updateById(d);
        return d.getId();
    }

    @Transactional
    public void delete(Long id) {
        Device d = requireMine(id);
        deviceMapper.deleteById(d.getId());
    }

    @Transactional
    public void setStatus(Long id, String status) {
        if (!"GOOD".equals(status) && !"FAULT".equals(status)) {
            throw new BizException("状态只能是良好或故障");
        }
        Device d = requireMine(id);
        d.setStatus(status);
        deviceMapper.updateById(d);
    }

    /** 设备是否适用某工序：按 processNames 精确匹配，未维护时按设备名关键字兜底。 */
    public boolean matchesProcess(Device d, String processName) {
        if (d == null || processName == null || processName.isBlank()) {
            return false;
        }
        if (StringUtils.hasText(d.getProcessNames())) {
            for (String p : d.getProcessNames().split("[,，、]")) {
                String t = p.trim();
                if (!t.isEmpty() && (processName.contains(t) || t.contains(processName))) {
                    return true;
                }
            }
            return false;
        }
        String guessed = guessProcessNames(d.getName());
        for (String p : guessed.split(",")) {
            if (!p.isEmpty() && (processName.contains(p) || p.contains(processName))) {
                return true;
            }
        }
        return false;
    }

    /** 按适用工序聚合本厂设备日产能（能力档案只读展示用）。 */
    public List<java.util.Map<String, Object>> capacityGroups(Long tenantId) {
        List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, tenantId));
        java.util.Map<String, int[]> agg = new java.util.LinkedHashMap<>();
        for (Device d : devices) {
            if ("FAULT".equals(normalizeStatus(d.getStatus()))) {
                continue;
            }
            String names = StringUtils.hasText(d.getProcessNames())
                    ? d.getProcessNames() : guessProcessNames(d.getName());
            for (String p : names.split("[,，、]")) {
                String t = p.trim();
                if (t.isEmpty()) {
                    continue;
                }
                int[] a = agg.computeIfAbsent(t, k -> new int[2]);
                a[0] += d.getDailyCapacity() == null ? 0 : d.getDailyCapacity();
                a[1]++;
            }
        }
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        agg.forEach((name, a) -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("processName", name);
            m.put("dailyCapacity", a[0]);
            m.put("deviceCount", a[1]);
            out.add(m);
        });
        return out;
    }

    /** 某工序的日产能 = 该厂适用该工序的设备日产能合计（能力档案只读展示用）。 */
    public int processCapacityOf(Long tenantId, String processName) {
        List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, tenantId));
        return devices.stream()
                .filter(d -> matchesProcess(d, processName))
                .mapToInt(d -> d.getDailyCapacity() == null ? 0 : d.getDailyCapacity())
                .sum();
    }

    public String guessProcessNames(String name) {
        if (name == null) {
            return "通用加工";
        }
        List<String> ps = new ArrayList<>();
        if (name.contains("车")) {
            ps.add("车削");
            ps.add("粗车");
            ps.add("精车");
        }
        if (name.contains("铣") || name.contains("加工中心")) {
            ps.add("铣削");
            ps.add("精铣");
            ps.add("CNC加工");
        }
        if (name.contains("磨")) {
            ps.add("磨削");
            ps.add("精磨");
        }
        if (name.contains("热处理") || name.contains("炉") || name.contains("淬")) {
            ps.add("热处理");
            ps.add("淬火");
            ps.add("回火");
        }
        if (name.contains("钻")) {
            ps.add("钻孔");
        }
        if (name.contains("镀") || name.contains("喷") || name.contains("氧化")) {
            ps.add("表面处理");
        }
        if (name.contains("检") || name.contains("测量") || name.contains("三坐标")) {
            ps.add("检测");
        }
        return ps.isEmpty() ? "通用加工" : String.join(",", ps);
    }

    @Transactional
    public void fillMissingProcessNames() {
        List<Device> list = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .isNull(Device::getProcessNames));
        for (Device d : list) {
            d.setProcessNames(guessProcessNames(d.getName()));
            deviceMapper.updateById(d);
        }
    }

    public List<Device> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>().in(Device::getId, ids));
    }

    public List<Long> parseIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public String writeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional
    public void fillMissingCapacity() {
        List<Device> list = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .isNull(Device::getDailyCapacity));
        for (Device d : list) {
            d.setDailyCapacity(guessDailyCapacity(d.getName()));
            deviceMapper.updateById(d);
        }
    }

    /** 从 capability_json.devices 迁移到 device 表（幂等） */
    @Transactional
    public void migrateFromCapability() {
        List<Enterprise> factories = enterpriseMapper.selectList(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, "FACTORY"));
        for (Enterprise e : factories) {
            long n = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                    .eq(Device::getTenantId, e.getId()));
            if (n > 0) {
                continue;
            }
            JsonNode node;
            try {
                if (!StringUtils.hasText(e.getCapabilityJson())) {
                    continue;
                }
                node = objectMapper.readTree(e.getCapabilityJson());
            } catch (Exception ex) {
                continue;
            }
            JsonNode devices = node.path("devices");
            if (!devices.isArray()) {
                continue;
            }
            for (JsonNode d : devices) {
                String name = d.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                List<String> mats = new ArrayList<>();
                if (node.path("materials").isArray()) {
                    node.path("materials").forEach(m -> mats.add(m.asText()));
                }
                Device ent = new Device();
                ent.setTenantId(e.getId());
                ent.setName(name);
                ent.setModel(d.path("model").asText(null));
                ent.setPrecisionText(d.path("precision").asText(null));
                ent.setParts(mats.isEmpty() ? "通用零件" : String.join(",", mats));
                ent.setMaterials(String.join(",", mats));
                ent.setDailyCapacity(guessDailyCapacity(name));
                ent.setStatus("GOOD");
                deviceMapper.insert(ent);
            }
            // 每厂再补一台常用辅机，便于报名勾选
            Device extra = new Device();
            extra.setTenantId(e.getId());
            String factoryName = e.getName() == null ? "" : e.getName();
            if (factoryName.contains("热处理")) {
                extra.setName("箱式电阻炉");
                extra.setModel("SX2-12-10");
                extra.setPrecisionText("±8℃");
                extra.setParts("齿轮,轴类");
                extra.setMaterials("合金钢,碳钢");
            } else if (factoryName.contains("宏达") || factoryName.contains("粗")) {
                extra.setName("普通车床");
                extra.setModel("CA6140");
                extra.setPrecisionText("0.05mm");
                extra.setParts("轴类,盘类");
                extra.setMaterials("碳钢,合金钢");
            } else {
                extra.setName("数控铣床");
                extra.setModel("XK7132");
                extra.setPrecisionText("0.02mm");
                extra.setParts("壳体,法兰,底板");
                extra.setMaterials("铝合金,不锈钢,合金钢");
            }
            extra.setDailyCapacity(guessDailyCapacity(extra.getName()));
            extra.setStatus("GOOD");
            deviceMapper.insert(extra);
        }
    }

    private Device requireMine(Long id) {
        Device d = deviceMapper.selectById(id);
        if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
            throw new BizException("设备不存在或无权操作");
        }
        return d;
    }

    private void fill(Device d, Device req) {
        d.setName(req.getName().trim());
        d.setModel(req.getModel());
        d.setPrecisionText(req.getPrecisionText());
        d.setParts(req.getParts());
        d.setMaterials(req.getMaterials());
        d.setProcessNames(StringUtils.hasText(req.getProcessNames())
                ? req.getProcessNames().trim()
                : guessProcessNames(d.getName()));
        if (req.getDailyCapacity() != null && req.getDailyCapacity() > 0) {
            d.setDailyCapacity(req.getDailyCapacity());
        }
        if (StringUtils.hasText(req.getStatus())) {
            d.setStatus(normalizeStatus(req.getStatus()));
        }
    }

    public int dailyCapacitySum(List<Device> devices) {
        int sum = 0;
        if (devices == null) {
            return 0;
        }
        for (Device d : devices) {
            if (d != null && d.getDailyCapacity() != null && !"FAULT".equals(normalizeStatus(d.getStatus()))) {
                sum += d.getDailyCapacity();
            }
        }
        return sum;
    }

    public static String normalizeStatus(String status) {
        if ("FAULT".equals(status) || "MAINTENANCE".equals(status)) {
            return "FAULT";
        }
        return "GOOD";
    }

    public int guessDailyCapacity(String name) {
        if (name == null) {
            return 120;
        }
        if (name.contains("热处理") || name.contains("炉")) {
            return 80;
        }
        if (name.contains("磨")) {
            return 150;
        }
        if (name.contains("车")) {
            return 200;
        }
        if (name.contains("铣") || name.contains("加工中心")) {
            return 180;
        }
        return 120;
    }
}
