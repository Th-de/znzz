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

    @PostMapping("/{demandId}/generate-ai")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Solution>> generateAi(@PathVariable Long demandId) {
        return R.ok(solutionService.generateAi(demandId));
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

    @PostMapping("/item/{solutionId}/publish")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> publish(@PathVariable Long solutionId) {
        solutionService.publishToBuyer(solutionId);
        return R.ok();
    }
}
