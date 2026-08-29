package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.BiddingDtos.*;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.service.BiddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bidding")
@RequiredArgsConstructor
public class BiddingController {

    private final BiddingService biddingService;

    @PostMapping("/intention")
    @PreAuthorize("hasRole('FACTORY')")
    public R<IntentionStart> intention(@RequestBody IntentionRequest req) {
        return R.ok(biddingService.intention(req));
    }

    @PostMapping("/{id}/pay-intention")
    @PreAuthorize("hasRole('FACTORY')")
    public R<IntentionStart> payIntention(@PathVariable Long id) {
        return R.ok(biddingService.continueIntentionPay(id));
    }

    @PostMapping("/{id}/cancel-intention")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> cancelIntention(@PathVariable Long id) {
        biddingService.cancelIntention(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel-lock")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> cancelLock(@PathVariable Long id) {
        biddingService.cancelLock(id);
        return R.ok();
    }

    @PostMapping("/lock")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Long> lock(@RequestBody LockRequest req) {
        return R.ok(biddingService.lock(req));
    }

    /** 工厂思考期填报：实施方案+单价+分期交付，冻结 5% 保证金 */
    @PostMapping("/commit")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> commit(@RequestBody CommitRequest req) {
        biddingService.commit(req);
        return R.ok();
    }

    /** 工厂思考期退出：不参加，退回意向金 */
    @PostMapping("/exit/{demandId}")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> exit(@PathVariable Long demandId) {
        biddingService.exitDemand(demandId);
        return R.ok();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FACTORY')")
    public R<List<Quotation>> mine() {
        return R.ok(biddingService.listMine());
    }

    @GetMapping("/demand/{demandId}")
    public R<List<Quotation>> byDemand(@PathVariable Long demandId) {
        return R.ok(biddingService.listByDemand(demandId));
    }
}
