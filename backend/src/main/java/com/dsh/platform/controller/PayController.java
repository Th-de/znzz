package com.dsh.platform.controller;

import com.dsh.platform.domain.pay.AlipaySandboxChannel;
import com.dsh.platform.service.BiddingService;
import com.dsh.platform.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pay/alipay")
@RequiredArgsConstructor
public class PayController {

    private final AlipaySandboxChannel alipaySandboxChannel;
    private final OrderService orderService;
    private final BiddingService biddingService;

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        if (!alipaySandboxChannel.verify(params)) {
            log.warn("支付宝回调验签失败");
            return "fail";
        }
        String status = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
            return "success";
        }
        String outTradeNo = params.get("out_trade_no");
        Long intentionId = alipaySandboxChannel.parseIntentionId(outTradeNo);
        Long stageId = alipaySandboxChannel.parseStageId(outTradeNo);
        if (intentionId == null && stageId == null) {
            return "fail";
        }
        try {
            BigDecimal paid = params.get("total_amount") == null ? null : new BigDecimal(params.get("total_amount"));
            if (intentionId != null) {
                biddingService.confirmIntentionPaid(intentionId, paid);
            } else {
                orderService.confirmAlipayPaid(stageId, paid);
            }
            return "success";
        } catch (Exception e) {
            log.warn("支付宝回调入账失败", e);
            return "fail";
        }
    }
}
