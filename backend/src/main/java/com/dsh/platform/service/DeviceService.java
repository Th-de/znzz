package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Device;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.DeviceMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.QuotationMapper;
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
    private final QuotationMapper quotationMapper;
    private final ObjectMapper objectMapper;

    public List<Device> listMine() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, UserContext.tenantId())
                .orderByDesc(Device::getId));
    }

    public List<Device> listIdleMine() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getTenantId, UserContext.tenantId())
                .eq(Device::getStatus, "IDLE")
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
                d.setStatus("IDLE");
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
        if (!List.of("IDLE", "IN_USE", "MAINTENANCE").contains(status)) {
            throw new BizException("状态只能是空闲/使用中/维修中");
        }
        Device d = requireMine(id);
        d.setStatus(status);
        deviceMapper.updateById(d);
    }

    public void assertSelectable(Long tenantId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BizException("请勾选投入的设备");
        }
        for (Long id : deviceIds) {
            Device d = deviceMapper.selectById(id);
            if (d == null || !tenantId.equals(d.getTenantId())) {
                throw new BizException("设备不存在或不属于本厂");
            }
            if (!"IDLE".equals(d.getStatus())) {
                throw new BizException("设备「" + d.getName() + "」当前不可用（" + d.getStatus() + "）");
            }
        }
    }

    @Transactional
    public void markInUseByQuotation(Quotation q) {
        for (Long id : parseIds(q == null ? null : q.getDeviceIdsJson())) {
            Device d = deviceMapper.selectById(id);
            if (d != null && q.getTenantId().equals(d.getTenantId()) && !"MAINTENANCE".equals(d.getStatus())) {
                d.setStatus("IN_USE");
                deviceMapper.updateById(d);
            }
        }
    }

    @Transactional
    public void releaseByQuotation(Quotation q) {
        for (Long id : parseIds(q == null ? null : q.getDeviceIdsJson())) {
            Device d = deviceMapper.selectById(id);
            if (d != null && q.getTenantId().equals(d.getTenantId()) && "IN_USE".equals(d.getStatus())) {
                d.setStatus("IDLE");
                deviceMapper.updateById(d);
            }
        }
    }

    @Transactional
    public void releaseByDemand(Long demandId) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId));
        for (Quotation q : qs) {
            releaseByQuotation(q);
        }
    }

    @Transactional
    public void releaseLosers(Long demandId, List<Long> winFactoryIds) {
        List<Quotation> qs = quotationMapper.selectList(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId));
        for (Quotation q : qs) {
            if (winFactoryIds == null || !winFactoryIds.contains(q.getTenantId())) {
                releaseByQuotation(q);
            }
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
                ent.setStatus("IDLE");
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
            extra.setStatus("IDLE");
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
        if (req.getDailyCapacity() != null && req.getDailyCapacity() > 0) {
            d.setDailyCapacity(req.getDailyCapacity());
        }
        if (StringUtils.hasText(req.getStatus())) {
            d.setStatus(req.getStatus());
        }
    }

    public int dailyCapacitySum(List<Device> devices) {
        int sum = 0;
        if (devices == null) {
            return 0;
        }
        for (Device d : devices) {
            if (d != null && d.getDailyCapacity() != null) {
                sum += d.getDailyCapacity();
            }
        }
        return sum;
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
