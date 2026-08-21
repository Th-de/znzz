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
        seed("13000000001", "BUYER", "演示买家", "DEMOBUYER01", 70, null);
        seed("13000000002", "FACTORY", "演示精加工厂", "DEMOFACT01", 78,
                cap("精加工中心", "VMC850", "0.01", "不锈钢", "铣削", "ISO9001", "精加工", 800, 0.99));
        seed("13000000003", "FACTORY", "演示粗加工厂", "DEMOFACT02", 68,
                cap("数控车床", "CK6150", "0.05", "碳钢", "车削", "ISO9001", "粗加工", 2000, 0.96));
        seed("13000000004", "FACTORY", "演示热处理厂", "DEMOFACT03", 75,
                cap("井式炉", "RQ3-75", "±5℃", "合金钢", "热处理", "IATF16949", "热处理", 600, 0.98));
        seed("13000000005", "FACTORY", "演示综合厂", "DEMOFACT04", 72,
                cap("加工中心", "HMC500", "0.02", "铝合金", "铣削", "ISO9001", "精加工", 500, 0.97));
        log.info("演示账号已就绪：买家 13000000001，工厂 13000000002～005，密码均为 123456");
    }

    private void seed(String phone, String type, String name, String creditCode,
                      int credit, String capability) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0) {
            return;
        }
        Enterprise e = new Enterprise();
        e.setType(type);
        e.setName(name);
        e.setCreditCode(creditCode);
        e.setCreditScore(credit);
        e.setAuthStatus("APPROVED");
        e.setContactName(name);
        e.setCapabilityJson(capability);
        enterpriseMapper.insert(e);
        SysUser u = new SysUser();
        u.setTenantId(e.getId());
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setRole(type);
        u.setRealName(name);
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    private String cap(String device, String model, String precision, String material,
                       String process, String cert, String processName, int daily, double yield) {
        return """
                {"devices":[{"name":"%s","model":"%s","precision":"%s","qty":2}],\
                "materials":["%s"],"processes":["%s"],"certs":["%s"],\
                "inspectDevices":"二次元","yieldRate":%s,\
                "capacityByProcess":[{"processName":"%s","dailyCapacity":%d,"loadPct":40}]}
                """.formatted(device, model, precision, material, process, cert, yield, processName, daily);
    }
}
