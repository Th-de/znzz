package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.dto.AuthDtos.UpdateAccountRequest;
import com.dsh.platform.dto.AuthDtos.UpdateEnterpriseRequest;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.SysUser;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnterpriseAdminService {

    private static final Set<String> MANAGE_TYPES = Set.of("BUYER", "FACTORY", "INSPECTION");

    private final EnterpriseMapper enterpriseMapper;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public List<Enterprise> listManage(String type, String keyword) {
        if (!StringUtils.hasText(type) || !MANAGE_TYPES.contains(type)) {
            throw new BizException("请选择需求方/工厂方/质检方");
        }
        List<Enterprise> list = enterpriseMapper.selectList(new LambdaQueryWrapper<Enterprise>()
                .eq(Enterprise::getType, type)
                .orderByDesc(Enterprise::getId));
        enrichUsers(list);

        if (!StringUtils.hasText(keyword)) {
            return list;
        }
        String kw = keyword.trim().toLowerCase();
        List<Enterprise> filtered = new ArrayList<>();
        for (Enterprise e : list) {
            if (contains(e.getName(), kw)
                    || contains(e.getCreditCode(), kw)
                    || contains(e.getContactName(), kw)
                    || contains(e.getAccountPhone(), kw)
                    || contains(e.getRealName(), kw)
                    || contains(e.getAddress(), kw)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    @Transactional
    public void updateEnterprise(Long id, UpdateEnterpriseRequest req) {
        Enterprise e = requireManageable(id);
        if (req == null) {
            throw new BizException("请填写企业信息");
        }
        if (!StringUtils.hasText(req.name())) {
            throw new BizException("请填写企业名称");
        }
        if (StringUtils.hasText(req.creditCode())) {
            String code = req.creditCode().trim();
            Long cnt = enterpriseMapper.selectCount(new LambdaQueryWrapper<Enterprise>()
                    .eq(Enterprise::getCreditCode, code)
                    .ne(Enterprise::getId, id));
            if (cnt != null && cnt > 0) {
                throw new BizException("该信用代码已被其他企业使用");
            }
            e.setCreditCode(code);
        }
        e.setName(req.name().trim());
        if (req.contactName() != null) {
            e.setContactName(req.contactName().trim());
        }
        if (req.address() != null) {
            e.setAddress(req.address().trim());
        }
        if (req.creditScore() != null) {
            if (req.creditScore() < 0 || req.creditScore() > 100) {
                throw new BizException("信用分须在 0~100");
            }
            e.setCreditScore(req.creditScore());
        }
        if (StringUtils.hasText(req.authStatus())) {
            if (!List.of("PENDING", "APPROVED", "REJECTED").contains(req.authStatus())) {
                throw new BizException("认证状态无效");
            }
            e.setAuthStatus(req.authStatus());
        }
        enterpriseMapper.updateById(e);
    }

    @Transactional
    public void updateAccount(Long enterpriseId, UpdateAccountRequest req) {
        Enterprise e = requireManageable(enterpriseId);
        if (req == null) {
            throw new BizException("请填写账号信息");
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, e.getId())
                .orderByAsc(SysUser::getId)
                .last("limit 1"));
        if (u == null) {
            throw new BizException("该企业尚无登录账号");
        }
        if ("SUPER_ADMIN".equals(u.getRole())) {
            throw new BizException("不能修改超级管理员账号");
        }
        if (StringUtils.hasText(req.phone())) {
            String phone = req.phone().trim();
            if (!phone.matches("^1\\d{10}$") && !"admin".equals(phone)) {
                throw new BizException("请填写11位手机号");
            }
            Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .ne(SysUser::getId, u.getId()));
            if (cnt != null && cnt > 0) {
                throw new BizException("该手机号已被占用");
            }
            u.setPhone(phone);
        }
        if (req.realName() != null) {
            u.setRealName(req.realName().trim());
        }
        if (StringUtils.hasText(req.status())) {
            if (!"ENABLED".equals(req.status()) && !"DISABLED".equals(req.status())) {
                throw new BizException("账号状态只能是启用或停用");
            }
            u.setStatus(req.status());
        }
        if (StringUtils.hasText(req.password())) {
            if (req.password().length() < 6) {
                throw new BizException("密码至少 6 位");
            }
            u.setPassword(passwordEncoder.encode(req.password()));
        }
        userMapper.updateById(u);
    }

    @Transactional
    public void softDelete(Long id) {
        Enterprise e = requireManageable(id);
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, e.getId()));
        for (SysUser u : users) {
            if ("SUPER_ADMIN".equals(u.getRole())) {
                throw new BizException("不能删除超级管理员关联企业");
            }
            u.setStatus("DISABLED");
            userMapper.updateById(u);
        }
        enterpriseMapper.deleteById(e.getId());
    }

    private Enterprise requireManageable(Long id) {
        Enterprise e = enterpriseMapper.selectById(id);
        if (e == null) {
            throw new BizException("企业不存在");
        }
        if ("PLATFORM".equals(e.getType()) || !MANAGE_TYPES.contains(e.getType())) {
            throw new BizException("该类企业不可在此管理");
        }
        return e;
    }

    private void enrichUsers(List<Enterprise> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(Enterprise::getId).toList();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getTenantId, ids)
                .orderByAsc(SysUser::getId));
        Map<Long, SysUser> first = users.stream()
                .collect(Collectors.toMap(SysUser::getTenantId, u -> u, (a, b) -> a));
        for (Enterprise e : list) {
            SysUser u = first.get(e.getId());
            if (u != null) {
                e.setUserId(u.getId());
                e.setAccountPhone(u.getPhone());
                e.setUserStatus(u.getStatus());
                e.setRealName(u.getRealName());
            }
        }
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase().contains(kw);
    }
}
