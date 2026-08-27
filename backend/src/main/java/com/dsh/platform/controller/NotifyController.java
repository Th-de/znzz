package com.dsh.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.R;
import com.dsh.platform.domain.notify.NotifyActionResolver;
import com.dsh.platform.dto.NotifyView;
import com.dsh.platform.dto.OrderDtos.TodoItem;
import com.dsh.platform.service.TodoService;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Notify;
import com.dsh.platform.mapper.CreditEventMapper;
import com.dsh.platform.mapper.EnterpriseMapper;
import com.dsh.platform.mapper.FundFlowMapper;
import com.dsh.platform.mapper.NotifyMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyMapper notifyMapper;
    private final FundFlowMapper fundFlowMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final CreditEventMapper creditEventMapper;
    private final NotifyActionResolver notifyActionResolver;
    private final TodoService todoService;

    @GetMapping("/todos")
    public R<List<TodoItem>> todos() {
        return R.ok(todoService.mine());
    }

    @GetMapping("/notifies")
    public R<List<NotifyView>> notifies() {
        List<Notify> list = notifyMapper.selectList(new LambdaQueryWrapper<Notify>()
                .eq(Notify::getTenantId, UserContext.tenantId())
                .orderByDesc(Notify::getId));
        String role = UserContext.role();
        return R.ok(list.stream().map(n -> notifyActionResolver.view(n, role)).toList());
    }

    @GetMapping("/funds")
    public R<List<FundFlow>> funds() {
        return R.ok(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getTenantId, UserContext.tenantId())
                .orderByDesc(FundFlow::getId)));
    }

    @GetMapping("/credits")
    public R<List<CreditEvent>> credits() {
        return R.ok(creditEventMapper.selectList(new LambdaQueryWrapper<CreditEvent>()
                .eq(CreditEvent::getTenantId, UserContext.tenantId())
                .orderByDesc(CreditEvent::getId)));
    }

    @GetMapping("/all-funds")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<FundFlow>> allFunds() {
        List<FundFlow> list = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .orderByDesc(FundFlow::getId));
        for (FundFlow f : list) {
            Enterprise e = f.getTenantId() == null ? null : enterpriseMapper.selectById(f.getTenantId());
            f.setEnterpriseName(e == null ? "" : e.getName());
        }
        return R.ok(list);
    }
}
