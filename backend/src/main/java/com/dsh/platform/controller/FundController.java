package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.entity.Account;
import com.dsh.platform.entity.FundFlow;
import com.dsh.platform.service.FundQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fund")
@RequiredArgsConstructor
public class FundController {

    private final FundQueryService fundQueryService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Map<String, Object>> overview() {
        return R.ok(fundQueryService.overview());
    }

    @GetMapping("/by-demand")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Map<String, Object>>> byDemand() {
        return R.ok(fundQueryService.byDemand());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Account>> accounts() {
        return R.ok(fundQueryService.accounts());
    }

    @GetMapping("/impound")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<FundFlow>> impound() {
        return R.ok(fundQueryService.impoundFlows());
    }

    @PostMapping("/impound/{flowId}/dispose")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> dispose(@PathVariable Long flowId, @RequestBody Map<String, String> body) {
        fundQueryService.disposeImpound(flowId,
                body == null ? null : body.get("action"),
                body == null ? null : body.get("remark"));
        return R.ok();
    }
}
