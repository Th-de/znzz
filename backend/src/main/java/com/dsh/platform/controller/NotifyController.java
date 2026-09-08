package com.dsh.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.R;
import com.dsh.platform.domain.notify.NotifyActionResolver;
import com.dsh.platform.domain.notify.NotifyDemandService;
import com.dsh.platform.dto.NotifyDemandRow;
import com.dsh.platform.dto.NotifyView;
import com.dsh.platform.dto.OrderDtos.TodoItem;
import com.dsh.platform.entity.CreditEvent;
import com.dsh.platform.service.CreditQueryService;
import com.dsh.platform.service.FundQueryService;
import com.dsh.platform.service.TodoService;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.entity.Notify;
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
    private final NotifyActionResolver notifyActionResolver;
    private final NotifyDemandService notifyDemandService;
    private final TodoService todoService;
    private final FundQueryService fundQueryService;
    private final CreditQueryService creditQueryService;

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

    @GetMapping("/notify-demands")
    public R<List<NotifyDemandRow>> notifyDemands() {
        return R.ok(notifyDemandService.mine());
    }

    @GetMapping("/funds")
    public R<List<FundFlow>> funds() {
        return R.ok(fundQueryService.mine(UserContext.tenantId()));
    }

    @GetMapping("/credits")
    public R<List<CreditEvent>> credits() {
        return R.ok(creditQueryService.mine(UserContext.tenantId()));
    }

    @GetMapping("/all-funds")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<FundFlow>> allFunds() {
        return R.ok(fundQueryService.all());
    }
}
