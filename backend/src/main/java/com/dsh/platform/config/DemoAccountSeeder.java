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
                78, "陈明远", "杭州市余杭区仓前街道文一西路 1500 号", null);
        seed("13000000002", "FACTORY", "宁波博锐精密机械有限公司", "91330205MA2H9C2D2B",
                82, "李建国", "宁波市鄞州区姜山镇科技园南路 88 号",
                cap("立式加工中心", "VMC850", "0.01mm",
                        "[\"20CrMnTi\",\"合金钢\",\"不锈钢\"]",
                        "[\"铣削\",\"精磨\",\"精加工\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"精磨\",\"dailyCapacity\":800,\"loadPct\":35}]",
                        0.99, "三坐标测量机"));
        seed("13000000003", "FACTORY", "台州宏达机械加工厂", "91331002MA2J1E3F3C",
                70, "王海峰", "台州市椒江区下陈街道机场路 216 号",
                cap("数控车床", "CK6150", "0.05mm",
                        "[\"20CrMnTi\",\"碳钢\",\"合金钢\"]",
                        "[\"粗车\",\"车削\",\"粗加工\"]",
                        "[\"ISO9001\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":2000,\"loadPct\":40}]",
                        0.96, "游标卡尺+千分尺"));
        seed("13000000004", "FACTORY", "嘉兴金盾热处理有限公司", "91330402MA2L4G5H5D",
                76, "赵丽华", "嘉兴市南湖区大桥镇工业园区兴工路 66 号",
                cap("井式渗碳炉", "RQ3-75", "±5℃",
                        "[\"20CrMnTi\",\"合金钢\"]",
                        "[\"热处理\",\"渗碳淬火\"]",
                        "[\"IATF16949\"]",
                        "[{\"processName\":\"热处理\",\"dailyCapacity\":600,\"loadPct\":45}]",
                        0.98, "硬度计+金相显微镜"));
        seed("13000000005", "FACTORY", "苏州汇通智能制造有限公司", "91320508MA1M6N7P7E",
                74, "周启明", "苏州市相城区黄埭镇潘阳工业园春丰路 18 号",
                cap("卧式加工中心", "HMC500", "0.02mm",
                        "[\"20CrMnTi\",\"铝合金\",\"合金钢\"]",
                        "[\"粗车\",\"热处理\",\"精磨\",\"铣削\"]",
                        "[\"ISO9001\",\"IATF16949\"]",
                        "[{\"processName\":\"粗车\",\"dailyCapacity\":900,\"loadPct\":30},{\"processName\":\"热处理\",\"dailyCapacity\":500,\"loadPct\":20},{\"processName\":\"精磨\",\"dailyCapacity\":700,\"loadPct\":30}]",
                        0.97, "三坐标+硬度计"));
        log.info("仿真账号已就绪：买家 13000000001，工厂 13000000002～005，密码均为 123456");
    }

    private void seed(String phone, String type, String companyName, String creditCode,
                      int credit, String contact, String address, String capability) {
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
                e.setAddress(address);
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
        e.setAddress(address);
        e.setCapabilityJson(capability);
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
