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

    /** 运营手动结束工厂思考期 */
    @PostMapping("/{demandId}/end-factory-thinking")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> endFactoryThinking(@PathVariable Long demandId) {
        flowService.endFactoryThinking(demandId);
        return R.ok();
    }

    /** 运营手动结束买家思考期（视为超时流单） */
    @PostMapping("/{demandId}/end-buyer-thinking")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> endBuyerThinking(@PathVariable Long demandId) {
        flowService.timeoutBuyerThinking(demandId);
        return R.ok();
    }

    /** 买家思考期决定：CONTINUE 交保证金 / CANCEL 取消全退 */
    @PostMapping("/{demandId}/buyer-decide")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> buyerDecide(@PathVariable Long demandId,
                               @RequestParam String action,
                               @RequestBody(required = false) CancelRequest req) {
        flowService.buyerDecide(demandId, action, req == null ? null : req.reason());
        return R.ok();
    }

    @PostMapping("/{demandId}/close-solution")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> closeSolution(@PathVariable Long demandId, @RequestBody(required = false) CancelRequest req) {
        flowService.closeAtSolution(demandId, req == null ? null : req.reason());
        return R.ok();
    }
}
