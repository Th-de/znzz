package com.dsh.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.Process;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.AttachmentMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.ProcessMapper;
import com.dsh.platform.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

@Slf4j
@Component
@Order(21)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_TITLE = "20CrMnTi齿轮轴多工序演示";

    private final SysUserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandMapper demandMapper;
    private final ProcessMapper processMapper;
    private final AttachmentMapper attachmentMapper;

    @Override
    public void run(String... args) throws Exception {
        enrichFactory("DEMOFACT01", capJson(
                "精加工中心", "VMC850", "0.01",
                "[\"20CrMnTi\",\"合金钢\",\"不锈钢\"]",
                "[\"精磨\",\"铣削\",\"精加工\"]",
                "[\"ISO9001\",\"IATF16949\"]",
                "[{\"processName\":\"精磨\",\"dailyCapacity\":800,\"loadPct\":35}]",
                0.99));
        enrichFactory("DEMOFACT02", capJson(
                "数控车床", "CK6150", "0.05",
                "[\"20CrMnTi\",\"碳钢\",\"合金钢\"]",
                "[\"粗车\",\"车削\",\"粗加工\"]",
                "[\"ISO9001\"]",
                "[{\"processName\":\"粗车\",\"dailyCapacity\":2000,\"loadPct\":40}]",
                0.96));
        enrichFactory("DEMOFACT03", capJson(
                "井式炉", "RQ3-75", "±5℃",
                "[\"20CrMnTi\",\"合金钢\"]",
                "[\"热处理\",\"渗碳淬火\"]",
                "[\"IATF16949\"]",
                "[{\"processName\":\"热处理\",\"dailyCapacity\":600,\"loadPct\":45}]",
                0.98));
        enrichFactory("DEMOFACT04", capJson(
                "加工中心", "HMC500", "0.02",
                "[\"20CrMnTi\",\"铝合金\",\"合金钢\"]",
                "[\"粗车\",\"热处理\",\"精磨\"]",
                "[\"ISO9001\",\"IATF16949\"]",
                "[{\"processName\":\"粗车\",\"dailyCapacity\":900,\"loadPct\":30},{\"processName\":\"热处理\",\"dailyCapacity\":500,\"loadPct\":20},{\"processName\":\"精磨\",\"dailyCapacity\":700,\"loadPct\":30}]",
                0.97));
        seedDemoDemand();
        log.info("演示数据已就绪：完整多工序需求「{}」", DEMO_TITLE);
    }

    private void enrichFactory(String creditCode, String capability) {
        Enterprise e = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getCreditCode, creditCode)
                .last("limit 1"));
        if (e == null) {
            return;
        }
        e.setCapabilityJson(capability);
        e.setAuthStatus("APPROVED");
        enterpriseMapper.updateById(e);
    }

    private void seedDemoDemand() throws Exception {
        SysUser buyer = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, "13000000001")
                .last("limit 1"));
        if (buyer == null) {
            return;
        }
        long exists = demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, buyer.getTenantId())
                .eq(Demand::getTitle, DEMO_TITLE));
        if (exists > 0) {
            return;
        }
        Demand d = new Demand();
        d.setTenantId(buyer.getTenantId());
        d.setTitle(DEMO_TITLE);
        d.setProductName("齿轮轴");
        d.setCategory("齿轮");
        d.setQuantity(1000);
        d.setMaterial("20CrMnTi");
        d.setTolerance("轴径 φ32h6 ±0.013，键槽对称度 0.02");
        d.setSurfaceTreatment("发黑防锈");
        d.setAql("1.0");
        d.setCertification("ISO9001,IATF16949");
        d.setMinYield(new BigDecimal("0.97"));
        d.setMinCreditScore(60);
        d.setDeadlineHard(LocalDate.now().plusDays(40));
        d.setDeadlineFlexible(LocalDate.now().plusDays(50));
        d.setDeliveryAddress("杭州市余杭区仓前街道演示工厂园 8 号");
        d.setPackaging("防锈油封+隔层纸+木箱，每箱 20 件");
        d.setMultiProcess(1);
        d.setWeightJson("{\"cost\":0.34,\"time\":0.33,\"quality\":0.33}");
        d.setIntentionDays(5);
        d.setRemark("演示单：三道工序分别报名，才能看出覆盖度和方案拆分。");
        d.setInspectMode("AQL");
        d.setGeneralTolerance("ISO 2768-m");
        d.setPartRevision("GEAR-SHAFT-001 / A");
        d.setExtraJson("{\"roughness\":\"1.6\",\"heatTreatment\":\"渗碳淬火 58-62HRC\",\"annualQty\":8000}");
        d.setReturnReason("");
        d.setCancelReason("");
        d.setStatus("PENDING_AUDIT");
        demandMapper.insert(d);

        insertProcess(d.getId(), 1, "粗车", 1000, "留磨量 0.3mm，同轴度 0.05");
        insertProcess(d.getId(), 2, "热处理", 1000, "渗碳层 0.8-1.2mm，58-62HRC");
        insertProcess(d.getId(), 3, "精磨", 1000, "φ32h6，Ra1.6，圆度 0.008");

        Path dir = Path.of("uploads").toAbsolutePath();
        Files.createDirectories(dir);
        Path dest = dir.resolve("demo-gear-shaft.md");
        String md = """
                # 齿轮轴工程说明 GEAR-SHAFT-001 / A

                - 材料：20CrMnTi
                - 数量：1000
                - 工序：粗车 → 热处理 → 精磨
                - 关键尺寸：轴径 φ32h6
                - 热处理：渗碳淬火 58-62HRC
                """;
        Files.writeString(dest, md, StandardCharsets.UTF_8);
        Attachment a = new Attachment();
        a.setBizType("DEMAND");
        a.setBizId(d.getId());
        a.setFileName("GEAR-SHAFT-001-工程图说明.md");
        a.setFilePath(dest.toString());
        a.setFileSize(Files.size(dest));
        a.setFileType("md");
        a.setUploaderId(buyer.getId());
        attachmentMapper.insert(a);
    }

    private void insertProcess(Long demandId, int no, String name, int qty, String req) {
        Process p = new Process();
        p.setDemandId(demandId);
        p.setProcessNo(no);
        p.setProcessName(name);
        p.setQuantity(qty);
        p.setRequirement(req);
        processMapper.insert(p);
    }

    private static String capJson(String device, String model, String precision,
                                 String materials, String processes, String certs,
                                 String capacity, double yield) {
        return """
                {"devices":[{"name":"%s","model":"%s","precision":"%s","qty":2}],\
                "materials":%s,"processes":%s,"certs":%s,\
                "inspectDevices":"三坐标+硬度计","yieldRate":%s,\
                "capacityByProcess":%s}
                """.formatted(device, model, precision, materials, processes, certs, yield, capacity);
    }
}
