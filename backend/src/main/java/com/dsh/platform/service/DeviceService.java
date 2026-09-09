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

    /** 每厂补齐到 target 台，便于买家详情分页展示。 */
    @Transactional
    public void ensureFactoryFleet(int target) {
        if (target <= 0) {
            return;
        }
        List<Enterprise> factories = enterpriseMapper.selectList(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, "FACTORY"));
        for (Enterprise e : factories) {
            List<Device> have = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                    .eq(Device::getTenantId, e.getId())
                    .orderByAsc(Device::getId));
            int n = have.size();
            String[][] catalog = fleetOf(e.getName());
            for (int i = n; i < target; i++) {
                String[] row = catalog[i % catalog.length];
                Device d = new Device();
                d.setTenantId(e.getId());
                d.setName(row[0] + "-" + String.format("%02d", i + 1));
                d.setModel(row[1]);
                d.setPrecisionText(row[2]);
                d.setProcessNames(row[3]);
                d.setDailyCapacity(Integer.parseInt(row[4]));
                d.setParts(row[5]);
                d.setMaterials(row[6]);
                d.setStatus(i % 17 == 0 ? "FAULT" : "GOOD");
                deviceMapper.insert(d);
            }
        }
    }

    private static String[][] fleetOf(String name) {
        String n = name == null ? "" : name;
        if (n.contains("热处理")) {
            return new String[][]{
                    {"井式渗碳炉", "RQ3-75", "±5℃", "热处理,渗碳淬火", "80", "齿轮,轴类", "20CrMnTi,合金钢"},
                    {"箱式电阻炉", "SX2-12-10", "±8℃", "热处理,回火", "90", "销轴,法兰", "合金钢,碳钢"},
                    {"网带淬火炉", "RCWC-300", "±6℃", "热处理,淬火", "70", "销轴", "合金钢"},
                    {"多用炉", "UMF-600", "±5℃", "热处理,渗碳淬火", "85", "齿轮", "20CrMnTi"},
                    {"金相试样磨抛机", "MP-2B", "—", "检测", "40", "试样", "合金钢"},
            };
        }
        if (n.contains("表面")) {
            return new String[][]{
                    {"阳极氧化线", "OX-800", "±2μm", "表面处理,阳极氧化", "400", "壳体,端盖", "铝合金"},
                    {"镀锌线", "ZN-12", "±3μm", "表面处理,电镀", "500", "紧固件", "碳钢"},
                    {"发黑槽", "BK-6", "—", "表面处理", "350", "轴类", "碳钢,合金钢"},
                    {"喷砂机", "SB-9080", "—", "表面处理", "280", "壳体", "铝合金,铸铁"},
                    {"膜厚仪工作台", "TT260", "0.1μm", "检测", "60", "抽检件", "铝合金"},
            };
        }
        if (n.contains("钣金")) {
            return new String[][]{
                    {"光纤激光切割机", "GF-3015", "0.1mm", "激光切割", "220", "护罩,机柜", "碳钢,不锈钢"},
                    {"数控折弯机", "WE67K-100", "0.2mm", "折弯", "180", "钣金件", "碳钢,不锈钢"},
                    {"二保焊机", "NBC-350", "—", "焊接", "150", "机柜", "碳钢"},
                    {"液压冲床", "JH21-80", "—", "冲压", "260", "支架", "碳钢"},
                    {"剪板机", "QC12Y-6", "0.2mm", "下料", "300", "板材", "碳钢,不锈钢"},
            };
        }
        if (n.contains("齿轮")) {
            return new String[][]{
                    {"滚齿机", "Y3150E", "7级", "滚齿", "90", "齿轮", "20CrMnTi"},
                    {"插齿机", "Y54", "7级", "插齿", "80", "内齿", "20CrMnTi"},
                    {"磨齿机", "Y7131", "5级", "磨齿,精磨", "60", "齿轮", "20CrMnTi"},
                    {"倒棱机", "Y9380", "—", "倒棱", "120", "齿轮", "合金钢"},
                    {"数控车床", "CK6150", "0.02mm", "粗车,精车", "180", "齿坯", "20CrMnTi,合金钢"},
            };
        }
        if (n.contains("宏达") || n.contains("武进")) {
            return new String[][]{
                    {"数控车床", "CK6150", "0.02mm", "粗车,精车,车削", "200", "轴类,盘类", "碳钢,合金钢"},
                    {"数控车床", "CK7520", "0.015mm", "精车,车削", "180", "法兰,套类", "不锈钢,合金钢"},
                    {"钻攻中心", "T500", "0.03mm", "钻孔", "220", "法兰", "碳钢"},
                    {"普通车床", "CA6140", "0.05mm", "粗车", "240", "轴类", "碳钢"},
                    {"外圆磨床", "M1432B", "0.005mm", "精磨,磨削", "140", "轴类", "合金钢"},
            };
        }
        return new String[][]{
                {"立式加工中心", "VMC850", "0.01mm", "铣削,精铣,CNC加工", "180", "壳体,法兰", "合金钢,不锈钢,铝合金"},
                {"卧式加工中心", "HMC500", "0.02mm", "铣削,精铣", "160", "箱体", "铸铁,合金钢"},
                {"数控车床", "CK6150", "0.02mm", "粗车,精车", "200", "轴类", "碳钢,合金钢"},
                {"数控铣床", "XK7132", "0.02mm", "铣削,精铣", "170", "底板,端盖", "铝合金,不锈钢"},
                {"外圆磨床", "M1432B", "0.005mm", "精磨,磨削", "140", "轴类", "合金钢"},
                {"钻攻中心", "T500", "0.03mm", "钻孔", "210", "法兰", "碳钢,铝合金"},
                {"三坐标测量机", "CMM-544", "0.002mm", "检测", "40", "抽检件", "通用"},
        };
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
