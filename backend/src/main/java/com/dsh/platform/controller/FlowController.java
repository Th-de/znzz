package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.DemandDtos.CancelRequest;
import com.dsh.platform.service.DemandService;
import com.dsh.platform.service.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flow")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;
    private final DemandService demandService;

    @PostMapping("/{demandId}/end-intention")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> endIntention(@PathVariable Long demandId) {
        flowService.endIntention(demandId);
        return R.ok();
    }

    @PostMapping("/{demandId}/cancel-intention")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> cancelIntention(@PathVariable Long demandId, @RequestBody CancelRequest req) {
        demandService.cancelPublished(demandId, req);
        return R.ok();
    }

    @PostMapping("/{demandId}/decide")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> decide(@PathVariable Long demandId,
                          @RequestParam String action,
                          @RequestBody(required = false) CancelRequest req) {
        flowService.decide(demandId, action, req == null ? null : req.reason());
        return R.ok();
    }

    @PostMapping("/{demandId}/review")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> review(@PathVariable Long demandId, @RequestParam boolean pass) {
        flowService.reviewCancel(demandId, pass);
        return R.ok();
    }

    @PostMapping("/{demandId}/end-locking")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> endLocking(@PathVariable Long demandId) {
        flowService.endLocking(demandId);
        return R.ok();
    }
}
