package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.service.CapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final CapabilityService capabilityService;

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
