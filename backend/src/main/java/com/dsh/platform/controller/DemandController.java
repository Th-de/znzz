package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.domain.coverage.CoverageView;
import com.dsh.platform.dto.DemandDtos.*;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Process;
import com.dsh.platform.service.DemandService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @PostMapping("/publish")
    @PreAuthorize("hasRole('BUYER')")
    public R<Long> publish(@RequestBody PublishRequest req) {
        return R.ok(demandService.publish(req));
    }

    @PostMapping("/{id}/republish")
    @PreAuthorize("hasRole('BUYER')")
    public R<Long> republish(@PathVariable Long id, @RequestBody PublishRequest req) {
        return R.ok(demandService.republish(id, req));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BUYER')")
    public R<List<Demand>> mine() {
        return R.ok(demandService.listMine());
    }

    @GetMapping("/published")
    public R<List<Demand>> published() {
        return R.ok(demandService.listPublished());
    }

    @GetMapping("/for-factory")
    @PreAuthorize("hasRole('FACTORY')")
    public R<List<Demand>> forFactory() {
        return R.ok(demandService.listForFactory());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Demand>> all() {
        return R.ok(demandService.listAll());
    }

    @GetMapping("/cancel-stats")
    @PreAuthorize("hasRole('BUYER')")
    public R<CancelStats> cancelStats() {
        return R.ok(demandService.cancelStats());
    }

    @GetMapping("/{id}")
    public R<DemandDetailView> detail(@PathVariable Long id) {
        return R.ok(demandService.detail(id));
    }

    @GetMapping("/{id}/coverage")
    public R<CoverageView> coverage(@PathVariable Long id) {
        return R.ok(demandService.coverage(id));
    }

    @GetMapping("/{id}/processes")
    public R<List<Process>> processes(@PathVariable Long id) {
        return R.ok(demandService.processes(id));
    }

    @GetMapping("/{id}/factories")
    @PreAuthorize("hasAnyRole('BUYER','OPERATOR','SUPER_ADMIN')")
    public R<List<Map<String, Object>>> bidFactories(@PathVariable Long id) {
        return R.ok(demandService.listBidFactories(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> cancel(@PathVariable Long id, @RequestBody CancelRequest req) {
        demandService.cancelPublished(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> returnToBuyer(@PathVariable Long id, @RequestBody ReturnRequest req) {
        demandService.returnToBuyer(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> audit(@PathVariable Long id, @RequestBody AuditRequest req) {
        demandService.audit(id, req);
        return R.ok();
    }
}
