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
import java.time.LocalDateTime;

@Slf4j
@Component
@Order(23)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_TITLE = "20CrMnTi齿轮轴粗车-热处理-精磨外协";
    private static final String DEMO_TITLE_2 = "铝合金壳体铣削精加工（待审）";

    private final SysUserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final DemandMapper demandMapper;
    private final ProcessMapper processMapper;
    private final AttachmentMapper attachmentMapper;

    @Override
    public void run(String... args) throws Exception {
        enrichFactory("91330205MA2H9C2D2B",
                "宁波博锐精密机械有限公司", "李建国", "宁波市鄞州区姜山镇科技园南路 88 号", 87,
                capJson("立式加工中心", "VMC850", "0.01mm",
                        "[\"20CrMnTi\",\"合金钢\",\"不锈钢\"]",
                        "[\"铣削\",\"精磨\",\"精加工\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"精磨\",\"dailyCapacity\":800,\"loadPct\":35}]",
                        0.99, "三坐标测量机"));
        enrichFactory("91331002MA2J1E3F3C",
                "台州宏达机械加工厂", "王海峰", "台州市椒江区下陈街道机场路 216 号", 73,
                capJson("数控车床", "CK6150", "0.05mm",
                        "[\"20CrMnTi\",\"碳钢\",\"合金钢\"]",
                        "[\"粗车\",\"车削\",\"粗加工\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":2000,\"loadPct\":40}]",
                        0.96, "游标卡尺+千分尺"));
        enrichFactory("91330402MA2L4G5H5D",
                "嘉兴金盾热处理有限公司", "赵丽华", "嘉兴市南湖区大桥镇工业园区兴工路 66 号", 81,
                capJson("井式渗碳炉", "RQ3-75", "±5℃",
                        "[\"20CrMnTi\",\"合金钢\"]",
                        "[\"热处理\",\"渗碳淬火\"]",
                        "[\"IATF16949\"]",
                        "[{\"processName\":\"热处理\",\"dailyCapacity\":600,\"loadPct\":45}]",
                        0.98, "硬度计+金相显微镜"));
        enrichFactory("91320508MA1M6N7P7E",
                "苏州汇通智能制造有限公司", "周启明", "苏州市相城区黄埭镇潘阳工业园春丰路 18 号", 79,
                capJson("卧式加工中心", "HMC500", "0.02mm",
                        "[\"20CrMnTi\",\"铝合金\",\"合金钢\"]",
                        "[\"粗车\",\"热处理\",\"精磨\",\"铣削\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":900,\"loadPct\":30},{\"processName\":\"热处理\",\"dailyCapacity\":500,\"loadPct\":20},{\"processName\":\"精磨\",\"dailyCapacity\":700,\"loadPct\":30}]",
                        0.97, "三坐标+硬度计"));
        enrichBuyer();
        seedPublishedGearShaft();
        seedPendingHousing();
        log.info("仿真业务数据已就绪：已发布「{}」，待审「{}」", DEMO_TITLE, DEMO_TITLE_2);
    }

    private void enrichBuyer() {
        Enterprise e = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getCreditCode, "91330110MA2K8B1X1A")
                .last("limit 1"));
        if (e == null) {
            return;
        }
        e.setName("杭州精工传动有限公司");
        e.setContactName("陈明远");
        e.setAddress("杭州市余杭区仓前街道文一西路 1500 号");
        e.setCreditScore(82);
        e.setAuthStatus("APPROVED");
        enterpriseMapper.updateById(e);
    }

    private void enrichFactory(String creditCode, String name, String contact, String address,
                               int credit, String capability) {
        Enterprise e = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getCreditCode, creditCode)
                .last("limit 1"));
        if (e == null) {
            return;
        }
        e.setName(name);
        e.setContactName(contact);
        e.setAddress(address);
        e.setCreditScore(credit);
        e.setCapabilityJson(capability);
        e.setAuthStatus("APPROVED");
        enterpriseMapper.updateById(e);
    }

    private void seedPublishedGearShaft() throws Exception {
        SysUser buyer = buyerUser();
        if (buyer == null) {
            return;
        }
        if (demandExists(buyer.getTenantId(), DEMO_TITLE)) {
            return;
        }
        Demand d = baseDemand(buyer.getTenantId());
        d.setTitle(DEMO_TITLE);
        d.setProductName("齿轮轴");
        d.setCategory("汽车传动件");
        d.setQuantity(1000);
        d.setMaterial("20CrMnTi");
        d.setTolerance("轴径 φ32h6 ±0.013，键槽对称度 0.02");
        d.setSurfaceTreatment("发黑防锈");
        d.setAql("1.0");
        d.setCertification("ISO9001,IATF16949");
        d.setMinYield(new BigDecimal("0.97"));
        d.setMinCreditScore(65);
        d.setDeadlineHard(LocalDate.now().plusDays(40));
        d.setDeadlineFlexible(null);
        d.setDeliveryAddress("杭州市余杭区仓前街道文一西路 1500 号 精工传动成品库");
        d.setPackaging("防锈油封+隔层纸+木箱，每箱 20 件");
        d.setMultiProcess(1);
        d.setWeightJson("{\"cost\":0.34,\"time\":0.33,\"quality\":0.33}");
        d.setIntentionDays(5);
        d.setRemark("多工序外协：粗车→热处理→精磨，请按工序分别报名。");
        d.setInspectMode("AQL");
        d.setGeneralTolerance("ISO 2768-m");
        d.setPartRevision("GEAR-SHAFT-001 / A");
        d.setExtraJson("{\"roughness\":\"Ra1.6\",\"heatTreatment\":\"渗碳淬火 58-62HRC\",\"annualQty\":8000}");
        d.setStatus("PUBLISHED");
        d.setIntentionEndAt(LocalDateTime.now().plusDays(5));
        demandMapper.insert(d);

        insertProcess(d.getId(), 1, "粗车", "留磨量 0.3mm，同轴度 0.05");
        insertProcess(d.getId(), 2, "热处理", "渗碳层 0.8-1.2mm，硬度 58-62HRC");
        insertProcess(d.getId(), 3, "精磨", "φ32h6，Ra1.6，圆度 0.008");
        attachMd(buyer, d.getId(), "GEAR-SHAFT-001-工程说明.md", """
                # 齿轮轴工程说明 GEAR-SHAFT-001 / A

                - 材料：20CrMnTi
                - 数量：1000 件
                - 工序：粗车 → 热处理 → 精磨
                - 关键尺寸：轴径 φ32h6
                - 热处理：渗碳淬火 58-62HRC
                - 交付：杭州精工传动成品库
                """);
    }

    private void seedPendingHousing() throws Exception {
        SysUser buyer = buyerUser();
        if (buyer == null) {
            return;
        }
        if (demandExists(buyer.getTenantId(), DEMO_TITLE_2)) {
            return;
        }
        Demand d = baseDemand(buyer.getTenantId());
        d.setTitle(DEMO_TITLE_2);
        d.setProductName("铝合金壳体");
        d.setCategory("精密结构件");
        d.setQuantity(500);
        d.setMaterial("6061-T6 铝合金");
        d.setTolerance("外形 ±0.05，安装孔位置度 0.03");
        d.setSurfaceTreatment("本色阳极氧化");
        d.setAql("1.5");
        d.setCertification("ISO9001");
        d.setMinYield(new BigDecimal("0.98"));
        d.setMinCreditScore(60);
        d.setDeadlineHard(LocalDate.now().plusDays(25));
        d.setDeadlineFlexible(null);
        d.setDeliveryAddress("杭州市余杭区仓前街道文一西路 1500 号");
        d.setPackaging("气泡袋+纸箱，防磕碰");
        d.setMultiProcess(0);
        d.setWeightJson("{\"cost\":0.4,\"time\":0.3,\"quality\":0.3}");
        d.setIntentionDays(3);
        d.setRemark("单工序铣削精加工，待运营审核通过后对外发布。");
        d.setInspectMode("AQL");
        d.setGeneralTolerance("ISO 2768-f");
        d.setPartRevision("ALU-HSG-12 / B");
        d.setExtraJson("{\"roughness\":\"Ra1.6\",\"annualQty\":3000}");
        d.setStatus("PENDING_AUDIT");
        demandMapper.insert(d);
        insertProcess(d.getId(), 1, "精铣", "外形轮廓与安装面一次装夹完成，去毛刺");
    }

    private Demand baseDemand(Long tenantId) {
        Demand d = new Demand();
        d.setTenantId(tenantId);
        d.setReturnReason("");
        d.setCancelReason("");
        return d;
    }

    private SysUser buyerUser() {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, "13000000001")
                .last("limit 1"));
    }

    private boolean demandExists(Long tenantId, String title) {
        return demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getTenantId, tenantId)
                .eq(Demand::getTitle, title)) > 0;
    }

    private void attachMd(SysUser buyer, Long demandId, String fileName, String md) throws Exception {
        Path dir = Path.of("uploads").toAbsolutePath();
        Files.createDirectories(dir);
        Path dest = dir.resolve(fileName);
        Files.writeString(dest, md, StandardCharsets.UTF_8);
        Attachment a = new Attachment();
        a.setBizType("DEMAND");
        a.setBizId(demandId);
        a.setFileName(fileName);
        a.setFilePath(dest.toString());
        a.setFileSize(Files.size(dest));
        a.setFileType("md");
        a.setUploaderId(buyer.getId());
        attachmentMapper.insert(a);
    }

    private void insertProcess(Long demandId, int no, String name, String req) {
        Process p = new Process();
        p.setDemandId(demandId);
        p.setProcessNo(no);
        p.setProcessName(name);
        p.setRequirement(req);
        processMapper.insert(p);
    }

    private static String capJson(String device, String model, String precision,
                                 String materials, String processes, String certs,
                                 String capacity, double yield, String inspect) {
        return """
                {"devices":[{"name":"%s","model":"%s","precision":"%s","qty":2}],\
                "materials":%s,"processes":%s,"certs":%s,\
                "inspectDevices":"%s","yieldRate":%s,\
                "capacityByProcess":%s}
                """.formatted(device, model, precision, materials, processes, certs, inspect, yield, capacity);
    }
}
