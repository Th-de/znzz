package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.OrderDtos.ReplaceFactoryRequest;
import com.dsh.platform.entity.Solution;
import com.dsh.platform.service.SolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/solution")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @PostMapping("/{demandId}/generate")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Solution>> generate(@PathVariable Long demandId) {
        return R.ok(solutionService.generate(demandId));
    }

    @GetMapping("/{demandId}")
    public R<List<Solution>> list(@PathVariable Long demandId) {
        return R.ok(solutionService.listByDemand(demandId));
    }

    @PostMapping("/{demandId}/custom")
    @PreAuthorize("hasRole('BUYER')")
    public R<Solution> saveCustom(@PathVariable Long demandId, @RequestBody List<Map<String, Object>> items) {
        return R.ok(solutionService.saveBuyerCustom(demandId, items));
    }

    @PostMapping("/{demandId}/generate-ai")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Solution>> generateAi(@PathVariable Long demandId,
                                       @RequestParam(defaultValue = "false") boolean force) {
        return R.ok(solutionService.generateAi(demandId, force));
    }

    @PostMapping("/item/{solutionId}/save-review")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Solution>> saveReview(@PathVariable Long solutionId, @RequestBody Map<String, Object> body) {
        return R.ok(solutionService.saveReview(solutionId, body));
    }

    @GetMapping("/item/{solutionId}/alternatives")
    public R<List<Map<String, Object>>> alternatives(@PathVariable Long solutionId,
                                                     @RequestParam Integer processNo) {
        return R.ok(solutionService.alternatives(solutionId, processNo));
    }

    @PostMapping("/item/{solutionId}/replace-factory")
    @PreAuthorize("hasAnyRole('BUYER','OPERATOR','SUPER_ADMIN')")
    public R<Void> replaceFactory(@PathVariable Long solutionId, @RequestBody ReplaceFactoryRequest req) {
        solutionService.replaceFactory(solutionId, req == null ? null : req.processNo(),
                req == null ? null : req.factoryId());
        return R.ok();
    }

    /** 某工序可分配候选（各厂承接量/单价/产能） */
    @GetMapping("/item/{solutionId}/candidates")
    public R<List<Map<String, Object>>> candidates(@PathVariable Long solutionId,
                                                   @RequestParam Integer processNo) {
        return R.ok(solutionService.allocationCandidates(solutionId, processNo));
    }

    /** 重新分配某工序在各厂之间的承接量（同工序可多厂分摊） */
    @PostMapping("/item/{solutionId}/reallocate")
    @PreAuthorize("hasAnyRole('BUYER','OPERATOR','SUPER_ADMIN')")
    public R<Void> reallocate(@PathVariable Long solutionId,
                              @RequestParam Integer processNo,
                              @RequestBody List<Map<String, Object>> allocations) {
        solutionService.reallocate(solutionId, processNo, allocations);
        return R.ok();
    }

    @PostMapping("/item/{solutionId}/publish")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> publish(@PathVariable Long solutionId) {
        solutionService.publishToBuyer(solutionId);
        return R.ok();
    }
}
