package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.entity.AuditLog;
import com.dsh.platform.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final com.dsh.platform.service.OpsOverviewService opsOverviewService;

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<AuditLog>> logs(@RequestParam(required = false) String action,
                                  @RequestParam(required = false) Integer limit) {
        return R.ok(auditService.list(action, limit));
    }

    /** 运营工作台概览：全平台待办与在途 */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<java.util.Map<String, Object>> overview() {
        return R.ok(opsOverviewService.overview());
    }
}
