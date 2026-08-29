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
        // 质检账号必须挂在独立的质检机构企业上，否则不会出现在用户管理的质检方列表
        Long tenantId;
        if ("INSPECTION".equals(req.role())) {
            String orgName = StringUtils.hasText(req.orgName())
                    ? req.orgName().trim()
                    : req.realName().trim() + "质检工作室";
            Enterprise org = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                    .eq(Enterprise::getType, "INSPECTION")
                    .eq(Enterprise::getName, orgName)
                    .last("limit 1"));
            if (org == null) {
                org = new Enterprise();
                org.setType("INSPECTION");
                org.setName(orgName);
                org.setCreditScore(100);
                org.setAuthStatus("APPROVED");
                org.setContactName(req.realName().trim());
                enterpriseMapper.insert(org);
                accountService.ensure(org.getId());
            }
            tenantId = org.getId();
        } else {
            tenantId = platformTenantId();
        }
        SysUser u = new SysUser();
        u.setTenantId(tenantId);
        u.setPhone(req.phone().trim());
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setRole(req.role());
        u.setRealName(req.realName().trim());
        u.setStatus("ENABLED");
        userMapper.insert(u);
    }

    private Long platformTenantId() {
        Enterprise p = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, "PLATFORM")
                .last("limit 1"));
        return p == null ? 1L : p.getId();
    }

    /** 修改密码：任何已登录角色可用。 */
    public void changePassword(Long userId, ChangePasswordRequest req) {
        if (req == null || !StringUtils.hasText(req.oldPassword()) || !StringUtils.hasText(req.newPassword())) {
            throw new BizException("请填写原密码和新密码");
        }
        if (req.newPassword().length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException("账号不存在");
        }
        if (!passwordEncoder.matches(req.oldPassword(), u.getPassword())) {
            throw new BizException("原密码错误");
        }
        u.setPassword(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(u);
    }

    /** 存量修复：把挂在平台租户下的质检账号迁到各自独立的质检机构企业（幂等）。 */
    public void migrateInspectionAccounts() {
        Long platformId = platformTenantId();
        List<SysUser> list = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "INSPECTION"));
        for (SysUser u : list) {
            Enterprise cur = enterpriseMapper.selectById(u.getTenantId());
            if (cur != null && "INSPECTION".equals(cur.getType())) {
                continue;
            }
            String orgName = (StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getPhone()) + "质检工作室";
            Enterprise org = enterpriseMapper.selectOne(new LambdaQueryWrapper<Enterprise>()
                    .eq(Enterprise::getType, "INSPECTION")
                    .eq(Enterprise::getName, orgName)
                    .last("limit 1"));
            if (org == null) {
                org = new Enterprise();
                org.setType("INSPECTION");
                org.setName(orgName);
                org.setCreditScore(100);
                org.setAuthStatus("APPROVED");
                org.setContactName(u.getRealName());
                enterpriseMapper.insert(org);
                accountService.ensure(org.getId());
            }
            if (!org.getId().equals(u.getTenantId()) || u.getTenantId().equals(platformId)) {
                u.setTenantId(org.getId());
                userMapper.updateById(u);
            }
        }
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
