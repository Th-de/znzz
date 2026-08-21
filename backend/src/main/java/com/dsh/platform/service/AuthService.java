package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.dto.AuthDtos.*;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SysUserMapper;
import com.dsh.platform.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public void register(RegisterRequest req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone())) > 0) {
            throw new BizException("该手机号已注册");
        }
        if (enterpriseMapper.selectCount(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getCreditCode, req.creditCode())) > 0) {
            throw new BizException("该企业已注册");
        }

        Enterprise e = new Enterprise();
        e.setType(req.type());
        e.setName(req.companyName());
        e.setCreditCode(req.creditCode());
        e.setCreditScore(60);
        e.setAuthStatus("APPROVED");
        e.setContactName(req.contactName());
        enterpriseMapper.insert(e);

        SysUser u = new SysUser();
        u.setTenantId(e.getId());
        u.setPhone(req.phone());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setRole(req.type());
        u.setRealName(req.contactName());
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    public LoginResponse login(LoginRequest req) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone()));
        if (u == null || !passwordEncoder.matches(req.password(), u.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        if (!"ENABLED".equals(u.getStatus())) {
            throw new BizException("账号已停用");
        }
        Enterprise e = enterpriseMapper.selectById(u.getTenantId());
        String token = jwtUtil.generate(u.getId(), u.getTenantId(), u.getRole());
        return new LoginResponse(token, u.getRole(), u.getTenantId(), e == null ? "" : e.getName());
    }

    /** 超级管理员创建运营/质检账号 */
    public void createUser(String phone, String password, String realName, String role) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)) > 0) {
            throw new BizException("该手机号已存在");
        }
        SysUser u = new SysUser();
        u.setTenantId(1L);   // 归属平台
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setRealName(realName);
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    /** 初始化超级管理员（部署时调用） */
    public void initSuperAdmin() {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "SUPER_ADMIN")) > 0) {
            return;
        }
        Enterprise e = new Enterprise();
        e.setType("PLATFORM");
        e.setName("平台方");
        e.setCreditScore(100);
        e.setAuthStatus("APPROVED");
        enterpriseMapper.insert(e);

        SysUser u = new SysUser();
        u.setTenantId(e.getId());
        u.setPhone("admin");
        u.setPassword(passwordEncoder.encode("admin123"));
        u.setRole("SUPER_ADMIN");
        u.setRealName("超级管理员");
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }
}
