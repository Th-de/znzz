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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccountService accountService;

    public void register(RegisterRequest req) {
        if (req == null) {
            throw new BizException("请填写注册信息");
        }
        if (!validPhone(req.phone())) {
            throw new BizException("请填写11位手机号");
        }
        if (!StringUtils.hasText(req.password()) || req.password().length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        if (!StringUtils.hasText(req.companyName())) {
            throw new BizException("请填写企业名称");
        }
        if (!StringUtils.hasText(req.creditCode()) || req.creditCode().trim().length() != 18) {
            throw new BizException("请填写18位统一社会信用代码");
        }
        if (!StringUtils.hasText(req.contactName())) {
            throw new BizException("请填写联系人");
        }
        if (!StringUtils.hasText(req.address())) {
            throw new BizException("请填写企业地址");
        }
        if (!"BUYER".equals(req.type()) && !"FACTORY".equals(req.type())) {
            throw new BizException("请选择企业类型");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone().trim())) > 0) {
            throw new BizException("该手机号已注册");
        }
        if (enterpriseMapper.selectCount(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getCreditCode, req.creditCode().trim())) > 0) {
            throw new BizException("该企业已注册");
        }

        Enterprise e = new Enterprise();
        e.setType(req.type());
        e.setName(req.companyName().trim());
        e.setCreditCode(req.creditCode().trim());
        e.setCreditScore(60);
        e.setAuthStatus("APPROVED");
        e.setContactName(req.contactName().trim());
        e.setAddress(req.address().trim());
        enterpriseMapper.insert(e);

        SysUser u = new SysUser();
        u.setTenantId(e.getId());
        u.setPhone(req.phone().trim());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setRole(req.type());
        u.setRealName(req.contactName().trim());
        u.setStatus("ENABLED");
        userMapper.insert(u);
        accountService.ensure(e.getId());
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null || !StringUtils.hasText(req.phone()) || !StringUtils.hasText(req.password())) {
            throw new BizException("请填写账号和密码");
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone().trim()));
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

    public void createUser(CreateUserRequest req) {
        if (req == null) {
            throw new BizException("请填写账号信息");
        }
        if (!StringUtils.hasText(req.phone())) {
            throw new BizException("请填写手机号");
        }
        if (!StringUtils.hasText(req.password()) || req.password().length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        if (!StringUtils.hasText(req.realName())) {
            throw new BizException("请填写姓名");
        }
        if (!"OPERATOR".equals(req.role()) && !"INSPECTION".equals(req.role())) {
            throw new BizException("角色只能是运营或质检");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.phone().trim())) > 0) {
            throw new BizException("该手机号已存在");
        }
        SysUser u = new SysUser();
        u.setTenantId(1L);
        u.setPhone(req.phone().trim());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setRole(req.role());
        u.setRealName(req.realName().trim());
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    public List<Enterprise> listEnterprises() {
        List<Enterprise> list = enterpriseMapper.selectList(new LambdaQueryWrapper<Enterprise>()
                .in(Enterprise::getType, "BUYER", "FACTORY", "INSPECTION")
                .orderByDesc(Enterprise::getId));
        if (list.isEmpty()) {
            return list;
        }
        List<Long> ids = list.stream().map(Enterprise::getId).toList();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getTenantId, ids));
        Map<Long, String> phones = users.stream()
                .collect(Collectors.toMap(SysUser::getTenantId, SysUser::getPhone, (a, b) -> a));
        for (Enterprise e : list) {
            e.setAccountPhone(phones.get(e.getId()));
        }
        return list;
    }

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
        accountService.ensure(e.getId());
    }

    private static boolean validPhone(String phone) {
        return phone != null && phone.trim().matches("^1\\d{10}$");
    }
}
