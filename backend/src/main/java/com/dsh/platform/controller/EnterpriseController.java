package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.AuthDtos.UpdateAccountRequest;
import com.dsh.platform.dto.AuthDtos.UpdateEnterpriseRequest;
import com.dsh.platform.entity.Enterprise;
import com.dsh.platform.service.AuthService;
import com.dsh.platform.service.CapabilityService;
import com.dsh.platform.service.EnterpriseAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final CapabilityService capabilityService;
    private final AuthService authService;
    private final EnterpriseAdminService enterpriseAdminService;

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Enterprise>> all() {
        return R.ok(authService.listEnterprises());
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Enterprise>> manage(@RequestParam String type,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(enterpriseAdminService.listManage(type, keyword));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> update(@PathVariable Long id, @RequestBody UpdateEnterpriseRequest req) {
        enterpriseAdminService.updateEnterprise(id, req);
        return R.ok();
    }

    @PutMapping("/{id}/account")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> updateAccount(@PathVariable Long id, @RequestBody UpdateAccountRequest req) {
        enterpriseAdminService.updateAccount(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        enterpriseAdminService.softDelete(id);
        return R.ok();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('BUYER','FACTORY')")
    public R<Map<String, Object>> mine() {
        return R.ok(capabilityService.mineProfile());
    }

    @GetMapping("/capability")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Map<String, Object>> get() {
        return R.ok(capabilityService.getMine());
    }

    @PutMapping("/capability")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> save(@RequestBody Map<String, Object> body) {
        capabilityService.save(body);
        return R.ok();
    }
}
