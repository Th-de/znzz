package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.DemandDtos.CancelRequest;
import com.dsh.platform.dto.OrderDtos.DecisionRequest;
import com.dsh.platform.dto.OrderDtos.DeliverRequest;
import com.dsh.platform.dto.OrderDtos.InspectRequest;
import com.dsh.platform.dto.OrderDtos.ProgressRequest;
import com.dsh.platform.dto.OrderDtos.SignRequest;
import com.dsh.platform.dto.OrderDtos.SurveyRequest;
import com.dsh.platform.dto.OrderDtos.UploadContractRequest;
import com.dsh.platform.entity.Inspection;
import com.dsh.platform.entity.StageProgressLog;
import com.dsh.platform.domain.pay.EscrowStart;
import com.dsh.platform.dto.OrderDtos.FactoryDemandJob;
import com.dsh.platform.dto.OrderDtos.OrderDetailView;
import com.dsh.platform.dto.OrderDtos.OrderListView;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Survey;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.service.ContractService;
import com.dsh.platform.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ContractService contractService;
    private final com.dsh.platform.domain.query.OrderQueryService orderQueryService;

    @PostMapping("/{demandId}/select/{solutionId}")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> confirm(@PathVariable Long demandId, @PathVariable Long solutionId) {
        orderService.confirmSolution(demandId, solutionId);
        return R.ok();
    }

    @PostMapping("/dispatch/{demandId}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Long> dispatch(@PathVariable Long demandId) {
        return R.ok(orderService.dispatch(demandId));
    }

    @GetMapping("/{orderId}")
    public R<OrderDetailView> one(@PathVariable Long orderId) {
        return R.ok(orderQueryService.detail(orderId));
    }

    @GetMapping("/{orderId}/contracts")
    public R<List<Contract>> contracts(@PathVariable Long orderId) {
        return R.ok(contractService.listByOrder(orderId));
    }

    @GetMapping("/{orderId}/contract")
    public R<Contract> contract(@PathVariable Long orderId) {
        return R.ok(contractService.getMine(orderId));
    }

    @GetMapping("/contracts/pending")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Contract>> pendingContracts() {
        return R.ok(contractService.listPendingReview());
    }

    /** 合同管理台账：全量合同（可按状态筛），运营用 */
    @GetMapping("/contracts/all")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<List<Contract>> allContracts(@RequestParam(required = false) String status) {
        return R.ok(contractService.listAll(status));
    }

    @PostMapping("/{orderId}/contract/upload")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> uploadContract(@PathVariable Long orderId, @RequestBody UploadContractRequest req) {
        contractService.upload(orderId, req == null ? null : req.factoryTenantId(),
                req == null ? null : req.attachmentId());
        return R.ok();
    }

    @PostMapping("/{orderId}/contract/buyer-sign")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> buyerSign(@PathVariable Long orderId, @RequestBody SignRequest req) {
        contractService.buyerSign(orderId, req == null ? null : req.factoryTenantId(),
                req != null && Boolean.TRUE.equals(req.read()),
                req == null ? null : req.sign());
        return R.ok();
    }

    @PostMapping("/{orderId}/contract/factory-sign")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> factorySign(@PathVariable Long orderId, @RequestBody SignRequest req) {
        contractService.factorySign(orderId, req != null && Boolean.TRUE.equals(req.read()),
                req == null ? null : req.sign());
        return R.ok();
    }

    @PostMapping("/{orderId}/contract/approve")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> approveContract(@PathVariable Long orderId, @RequestParam Long factoryTenantId) {
        contractService.approve(orderId, factoryTenantId);
        return R.ok();
    }

    @PostMapping("/stage/{stageId}/start")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> startStage(@PathVariable Long stageId) {
        orderService.startStage(stageId);
        return R.ok();
    }

    @PostMapping("/stage/{stageId}/progress")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> reportProgress(@PathVariable Long stageId, @RequestBody ProgressRequest req) {
        orderService.reportProgress(stageId, req);
        return R.ok();
    }

    @GetMapping("/stage/{stageId}/progress-log")
    public R<List<StageProgressLog>> progressLog(@PathVariable Long stageId) {
        return R.ok(orderService.progressLog(stageId));
    }

    @GetMapping("/stage/{stageId}/inspection")
    public R<Inspection> inspectionOf(@PathVariable Long stageId) {
        return R.ok(orderService.inspectionOf(stageId));
    }

    @PostMapping("/stage/{stageId}/deliver")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> deliver(@PathVariable Long stageId, @RequestBody(required = false) DeliverRequest req) {
        orderService.deliver(stageId, req == null ? null : req.deliveredQty());
        return R.ok();
    }

    @PostMapping("/stage/{stageId}/inspect")
    @PreAuthorize("hasAnyRole('INSPECTION','OPERATOR','SUPER_ADMIN')")
    public R<Void> inspect(@PathVariable Long stageId, @RequestBody InspectRequest req) {
        String role = com.dsh.platform.security.UserContext.role();
        if ("OPERATOR".equals(role) || "SUPER_ADMIN".equals(role)) {
            orderService.approveInspect(stageId, req);
        } else {
            orderService.inspect(stageId, req);
        }
        return R.ok();
    }

    @PostMapping({"/stage/{stageId}/inspect-approve", "/stage/{stageId}/inspect/approve"})
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
    public R<Void> approveInspect(@PathVariable Long stageId, @RequestBody InspectRequest req) {
        orderService.approveInspect(stageId, req);
        return R.ok();
    }

    @PostMapping("/stage/{stageId}/decision")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> decideInspect(@PathVariable Long stageId, @RequestBody DecisionRequest req) {
        orderService.decideInspect(stageId, req);
        return R.ok();
    }

    @PostMapping("/stage/{stageId}/inspect-fee")
    @PreAuthorize("hasAnyRole('BUYER','FACTORY')")
    public R<EscrowStart> payInspectFee(@PathVariable Long stageId) {
        return R.ok(orderService.payInspectFee(stageId));
    }

    @PostMapping("/stage/{stageId}/pay")
    @PreAuthorize("hasRole('BUYER')")
    public R<EscrowStart> payStage(@PathVariable Long stageId) {
        return R.ok(orderService.payStage(stageId));
    }

    @PostMapping("/stage/{stageId}/survey")
    @PreAuthorize("hasAnyRole('BUYER','FACTORY')")
    public R<Void> survey(@PathVariable Long stageId, @RequestBody SurveyRequest req) {
        orderService.submitSurvey(stageId, req);
        return R.ok();
    }

    @GetMapping("/stage/{stageId}/surveys")
    public R<List<Survey>> surveys(@PathVariable Long stageId) {
        return R.ok(orderService.surveysOfStage(stageId));
    }

    @PostMapping("/{orderId}/accept")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> accept(@PathVariable Long orderId) {
        orderService.accept(orderId);
        return R.ok();
    }

    @PostMapping("/{orderId}/cancel-by-buyer")
    @PreAuthorize("hasRole('BUYER')")
    public R<Void> cancelByBuyer(@PathVariable Long orderId, @RequestBody(required = false) CancelRequest req) {
        orderService.cancelByBuyer(orderId, req == null ? null : req.reason());
        return R.ok();
    }

    @PostMapping("/{orderId}/cancel-by-factory")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> cancelByFactory(@PathVariable Long orderId, @RequestBody(required = false) CancelRequest req) {
        orderService.cancelByFactory(orderId, req == null ? null : req.reason());
        return R.ok();
    }

    @GetMapping("/inspect/queue")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN','INSPECTION')")
    public R<List<WorkStage>> inspectQueue() {
        return R.ok(orderQueryService.inspectQueue());
    }

    @GetMapping("/{orderId}/stages")
    public R<List<WorkStage>> stages(@PathVariable Long orderId) {
        return R.ok(orderQueryService.stages(orderId));
    }

    @GetMapping("/my-jobs")
    @PreAuthorize("hasRole('FACTORY')")
    public R<List<FactoryDemandJob>> myJobs() {
        return R.ok(orderQueryService.myDemandJobs());
    }

    @GetMapping("/my-stages")
    @PreAuthorize("hasRole('FACTORY')")
    public R<List<WorkStage>> myStages() {
        return R.ok(orderQueryService.myStages());
    }

    @GetMapping("/all")
    public R<List<OrderListView>> all() {
        return R.ok(orderQueryService.listForCurrentUser());
    }
}
