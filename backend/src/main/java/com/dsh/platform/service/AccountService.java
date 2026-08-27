package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Account;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.mapper.AccountMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    public static final BigDecimal DEFAULT_BALANCE = new BigDecimal("1000000");

    private final AccountMapper accountMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Transactional
    public Account ensure(Long tenantId) {
        if (tenantId == null) {
            throw new BizException("企业不存在，无法开户");
        }
        Account a = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getTenantId, tenantId)
                .last("limit 1"));
        if (a != null) {
            return a;
        }
        Enterprise e = enterpriseMapper.selectById(tenantId);
        a = new Account();
        a.setTenantId(tenantId);
        if (e != null && "PLATFORM".equals(e.getType())) {
            a.setBalance(BigDecimal.ZERO);
        } else {
            a.setBalance(DEFAULT_BALANCE);
        }
        a.setFrozen(BigDecimal.ZERO);
        accountMapper.insert(a);
        return a;
    }

    public Account get(Long tenantId) {
        return ensure(tenantId);
    }

    /** 可用余额 → 冻结 */
    @Transactional
    public void freeze(Long tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account a = ensure(tenantId);
        BigDecimal bal = nvl(a.getBalance());
        if (bal.compareTo(amount) < 0) {
            throw new BizException("账户余额不足，当前可用 " + bal);
        }
        a.setBalance(bal.subtract(amount));
        a.setFrozen(nvl(a.getFrozen()).add(amount));
        accountMapper.updateById(a);
    }

    /** 冻结 → 可用余额 */
    @Transactional
    public void unfreeze(Long tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account a = ensure(tenantId);
        BigDecimal fr = nvl(a.getFrozen());
        BigDecimal take = amount.min(fr);
        a.setFrozen(fr.subtract(take));
        a.setBalance(nvl(a.getBalance()).add(take));
        accountMapper.updateById(a);
    }

    /** 扣减冻结（罚没等，钱离开该账户） */
    @Transactional
    public void deductFrozen(Long tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account a = ensure(tenantId);
        BigDecimal fr = nvl(a.getFrozen());
        if (fr.compareTo(amount) < 0) {
            throw new BizException("冻结余额不足");
        }
        a.setFrozen(fr.subtract(amount));
        accountMapper.updateById(a);
    }

    /** 扣减可用余额 */
    @Transactional
    public void debit(Long tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account a = ensure(tenantId);
        BigDecimal bal = nvl(a.getBalance());
        if (bal.compareTo(amount) < 0) {
            throw new BizException("账户余额不足，当前可用 " + bal);
        }
        a.setBalance(bal.subtract(amount));
        accountMapper.updateById(a);
    }

    /** 增加可用余额 */
    @Transactional
    public void credit(Long tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account a = ensure(tenantId);
        a.setBalance(nvl(a.getBalance()).add(amount));
        accountMapper.updateById(a);
    }

    /** 未锁价罚没：工厂冻结扣减 + 平台余额增加 */
    @Transactional
    public void impound(Long fromTenantId, Long platformTenantId, BigDecimal amount) {
        deductFrozen(fromTenantId, amount);
        credit(platformTenantId, amount);
    }

    public List<Account> listAll() {
        List<Account> list = accountMapper.selectList(new LambdaQueryWrapper<Account>().orderByDesc(Account::getId));
        for (Account a : list) {
            Enterprise e = enterpriseMapper.selectById(a.getTenantId());
            if (e != null) {
                a.setEnterpriseName(e.getName());
                a.setEnterpriseType(e.getType());
            }
        }
        return list;
    }

    /** 给存量企业补开户（幂等） */
    @Transactional
    public void ensureAllEnterprises() {
        List<Enterprise> all = enterpriseMapper.selectList(null);
        for (Enterprise e : all) {
            ensure(e.getId());
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
