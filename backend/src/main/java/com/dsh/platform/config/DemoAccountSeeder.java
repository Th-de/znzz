package com.dsh.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DemoAccountSeeder implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seed("13000000001", "BUYER", "杭州精工传动有限公司", "91330110MA2K8B1X1A",
                82, "陈明远", "杭州市余杭区仓前街道文一西路 1500 号", null,
                "面向减速机、齿轮轴与法兰的精密外协采购，月均需求稳定。");
        seed("13000000021", "BUYER", "上海临港智能装备有限公司", "91310115MA1H2C3D4A",
                80, "刘思远", "上海市浦东新区临港新片区海基六路 888 号", null,
                "新能源电驱壳体与端盖外协，关注交期与尺寸一致性。");
        seed("13000000022", "BUYER", "无锡华辰汽车零部件有限公司", "91320205MA2K3L4M5B",
                78, "高振宇", "无锡市新吴区梅村街道锡勤路 120 号", null,
                "乘用车传动与转向件批量外协，要求 IATF 体系。");
        seed("13000000023", "BUYER", "武汉光谷精密传动有限公司", "91420100MA4N6P7Q8C",
                76, "韩雪", "武汉市东湖高新区光谷大道 77 号", null,
                "机器人关节与谐波减速机零件外协。");
        seed("13000000024", "BUYER", "成都西部轨道交通装备有限公司", "91510100MA6R8S9T0D",
                84, "唐伟", "成都市郫都区现代工业港南片区", null,
                "轨道车辆制动与转向架零件外协。");
        seed("13000000025", "BUYER", "青岛海工船舶配套有限公司", "91370202MA7U1V2W3E",
                75, "孙丽", "青岛市黄岛区长江西路 200 号", null,
                "船用泵阀与法兰小批量多品种外协。");
        seed("13000000026", "BUYER", "西安航空附件制造有限公司", "91610113MA8X4Y5Z6F",
                88, "马骏", "西安市阎良区蓝天路 18 号", null,
                "航空附件壳体与接头，强调全检与可追溯。");
        seed("13000000027", "BUYER", "长沙中联工程机械配套有限公司", "91430104MA9A2B3C4G",
                77, "彭辉", "长沙市岳麓区麓谷大道 658 号", null,
                "工程机械液压阀块与销轴外协。");
        seed("13000000028", "BUYER", "沈阳机床配套协作有限公司", "91210104MAB5D6E7F8",
                74, "赵宇", "沈阳市铁西区北一西路 50 号", null,
                "机床主轴套与法兰盘外协。");
        seed("13000000029", "BUYER", "东莞松山湖电子结构件有限公司", "91441900MAC8G9H0J1",
                79, "林晓", "东莞市松山湖科技十路 1 号", null,
                "消费电子铝合金壳体与散热件外协。");

        seed("13000000002", "FACTORY", "宁波博锐精密机械有限公司", "91330205MA2H9C2D2B",
                87, "李建国", "宁波市鄞州区姜山镇科技园南路 88 号",
                cap("立式加工中心", "VMC850", "0.01mm",
                        "[\"20CrMnTi\",\"合金钢\",\"不锈钢\"]",
                        "[\"铣削\",\"精磨\",\"精加工\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"精磨\",\"dailyCapacity\":800,\"loadPct\":35}]",
                        0.99, "三坐标测量机"),
                "擅长高精度铣削与磨削，服务减速机与汽车传动件。");
        seed("13000000003", "FACTORY", "台州宏达机械加工厂", "91331002MA2J1E3F3C",
                73, "王海峰", "台州市椒江区下陈街道机场路 216 号",
                cap("数控车床", "CK6150", "0.05mm",
                        "[\"20CrMnTi\",\"碳钢\",\"合金钢\"]",
                        "[\"粗车\",\"车削\",\"粗加工\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":2000,\"loadPct\":40}]",
                        0.96, "游标卡尺+千分尺"),
                "轴类粗车产能充足，适合大批量预备加工。");
        seed("13000000004", "FACTORY", "嘉兴金盾热处理有限公司", "91330402MA2L4G5H5D",
                81, "赵丽华", "嘉兴市南湖区大桥镇工业园区兴工路 66 号",
                cap("井式渗碳炉", "RQ3-75", "±5℃",
                        "[\"20CrMnTi\",\"合金钢\"]",
                        "[\"热处理\",\"渗碳淬火\"]",
                        "[\"IATF16949\"]",
                        "[{\"processName\":\"热处理\",\"dailyCapacity\":600,\"loadPct\":45}]",
                        0.98, "硬度计+金相显微镜"),
                "渗碳淬火与调质，服务齿轮轴与销轴。");
        seed("13000000005", "FACTORY", "苏州汇通智能制造有限公司", "91320508MA1M6N7P7E",
                79, "周启明", "苏州市相城区黄埭镇潘阳工业园春丰路 18 号",
                cap("卧式加工中心", "HMC500", "0.02mm",
                        "[\"20CrMnTi\",\"铝合金\",\"合金钢\"]",
                        "[\"粗车\",\"热处理\",\"精磨\",\"铣削\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":900,\"loadPct\":30},{\"processName\":\"热处理\",\"dailyCapacity\":500,\"loadPct\":20},{\"processName\":\"精磨\",\"dailyCapacity\":700,\"loadPct\":30}]",
                        0.97, "三坐标+硬度计"),
                "多工序协同，可承接壳体与法兰组合加工。");
        seed("13000000006", "FACTORY", "温州瓯海精密铸造有限公司", "91330304MAD2K3L4M5",
                76, "陈波", "温州市瓯海区娄桥街道南威大道 36 号",
                cap("立式加工中心", "VMC1060", "0.02mm",
                        "[\"铸铁\",\"铝合金\",\"碳钢\"]",
                        "[\"铣削\",\"精铣\",\"钻孔\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"精铣\",\"dailyCapacity\":700,\"loadPct\":38}]",
                        0.96, "三坐标测量机"),
                "铸件后续精铣与钻孔，阀体阀盖经验丰富。");
        seed("13000000007", "FACTORY", "常州武进数控机床协作厂", "91320412MAE6N7P8Q9",
                82, "吴磊", "常州市武进区湖塘镇东大道 188 号",
                cap("数控车床", "CK7520", "0.015mm",
                        "[\"合金钢\",\"不锈钢\",\"45#\"]",
                        "[\"精车\",\"车削\",\"钻孔\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"精车\",\"dailyCapacity\":1100,\"loadPct\":42}]",
                        0.98, "圆度仪+千分尺"),
                "盘类与轴类精车，主轴套与法兰盘交期稳定。");
        seed("13000000008", "FACTORY", "无锡惠山模具制造有限公司", "91320206MAF1R2S3T4",
                80, "蒋敏", "无锡市惠山区洛社镇杨市路 9 号",
                cap("加工中心", "VMC1370", "0.008mm",
                        "[\"模具钢\",\"铝合金\",\"不锈钢\"]",
                        "[\"精铣\",\"CNC加工\",\"磨削\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"精铣\",\"dailyCapacity\":500,\"loadPct\":33}]",
                        0.985, "三坐标+投影仪"),
                "模具与高精度型腔铣削，适合小批量精密件。");
        seed("13000000009", "FACTORY", "杭州萧山表面处理有限公司", "91330109MAG5U6V7W8",
                74, "徐芳", "杭州市萧山区瓜沥镇东恩路 72 号",
                cap("阳极氧化线", "OX-800", "膜厚 ±2μm",
                        "[\"铝合金\",\"不锈钢\"]",
                        "[\"表面处理\",\"阳极氧化\",\"电镀\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"表面处理\",\"dailyCapacity\":1500,\"loadPct\":50}]",
                        0.97, "膜厚仪"),
                "阳极氧化、发黑与镀锌，可与机加厂协同交付。");
        seed("13000000010", "FACTORY", "绍兴柯桥齿轮传动加工厂", "91330621MAH9X0Y1Z2",
                85, "何建", "绍兴市柯桥区齐贤街道齐贤大道 56 号",
                cap("滚齿机", "Y3150E", "7级精度",
                        "[\"20CrMnTi\",\"合金钢\"]",
                        "[\"滚齿\",\"插齿\",\"磨齿\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"滚齿\",\"dailyCapacity\":400,\"loadPct\":40}]",
                        0.99, "齿轮测量中心"),
                "齿轮滚插磨一条龙，适合减速机齿轮外协。");
        seed("13000000011", "FACTORY", "湖州德清钣金智造有限公司", "91330521MAJ3A4B5C6",
                72, "沈涛", "湖州市德清县武康镇中兴北路 518 号",
                cap("激光切割机", "GF-3015", "0.1mm",
                        "[\"碳钢\",\"不锈钢\",\"铝合金\"]",
                        "[\"激光切割\",\"折弯\",\"焊接\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"激光切割\",\"dailyCapacity\":900,\"loadPct\":36}]",
                        0.95, "卡尺+直角尺"),
                "钣金下料折弯焊接，适合机柜与护罩结构件。");
        log.info("仿真账号已就绪：10 家买家（13000000001、13000000021～029），10 家工厂（13000000002～011），密码均为 123456");
    }

    private void seed(String phone, String type, String companyName, String creditCode,
                      int credit, String contact, String address, String capability, String introduction) {
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .last("limit 1"));
        if (existing != null) {
            Enterprise e = enterpriseMapper.selectById(existing.getTenantId());
            if (e != null) {
                e.setName(companyName);
                e.setCreditCode(creditCode);
                e.setCreditScore(credit);
                e.setAuthStatus("APPROVED");
                e.setContactName(contact);
                e.setContactPhone(phone);
                e.setAddress(address);
                e.setIntroduction(introduction);
                if (capability != null) {
                    e.setCapabilityJson(capability);
                }
                enterpriseMapper.updateById(e);
            }
            existing.setRealName(contact);
            existing.setRole(type);
            existing.setStatus("ENABLED");
            existing.setPassword(passwordEncoder.encode("123456"));
            existing.setPasswordPlain("123456");
            userMapper.updateById(existing);
            return;
        }
        Enterprise e = new Enterprise();
        e.setType(type);
        e.setName(companyName);
        e.setCreditCode(creditCode);
        e.setCreditScore(credit);
        e.setAuthStatus("APPROVED");
        e.setContactName(contact);
        e.setContactPhone(phone);
        e.setAddress(address);
        e.setCapabilityJson(capability);
        e.setIntroduction(introduction);
        enterpriseMapper.insert(e);

        SysUser u = new SysUser();
        u.setTenantId(e.getId());
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setPasswordPlain("123456");
        u.setRole(type);
        u.setRealName(contact);
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    private String cap(String device, String model, String precision,
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
