package com.dsh.platform.domain.pay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.dsh.platform.common.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipaySandboxChannel {

    private final ObjectMapper objectMapper;

    @Value("${dsh.pay.alipay.enabled:false}")
    private boolean enabled;
    @Value("${dsh.pay.alipay.app-id:}")
    private String appId;
    @Value("${dsh.pay.alipay.gateway:https://openapi-sandbox.dl.alipaydev.com/gateway.do}")
    private String gateway;
    @Value("${dsh.pay.alipay.app-private-key:}")
    private String appPrivateKey;
    @Value("${dsh.pay.alipay.alipay-public-key:}")
    private String alipayPublicKey;
    @Value("${dsh.pay.alipay.notify-url:}")
    private String notifyUrl;
    @Value("${dsh.pay.alipay.return-url:http://localhost:5173/buyer/orders}")
    private String returnUrl;
    @Value("${dsh.pay.alipay.factory-return-url:http://localhost:5173/factory/quotations}")
    private String factoryReturnUrl;

    public boolean ready() {
        return enabled
                && notBlank(appId)
                && notBlank(appPrivateKey)
                && notBlank(alipayPublicKey);
    }

    public EscrowStart startPay(Long stageId, BigDecimal amount, String subject) {
        return startPay(outTradeNo(stageId), amount, subject, returnUrl);
    }

    public EscrowStart startIntentionPay(Long quotationId, BigDecimal amount) {
        return startPay("INTENTION-" + quotationId, amount, "意向金#" + quotationId, factoryReturnUrl);
    }

    public EscrowStart startPay(String outTradeNo, BigDecimal amount, String subject, String backUrl) {
        if (!ready()) {
            throw new BizException("支付宝沙箱未配置");
        }
        if (!notBlank(notifyUrl)) {
            throw new BizException("未配置 NATAPP 回调地址。买好免费隧道后，把 https://你的域名/api/pay/alipay/notify 填进 application-local.yml 的 dsh.pay.alipay.notify-url");
        }
        try {
            AlipayClient client = new DefaultAlipayClient(
                    gateway, appId, appPrivateKey, "json", "UTF-8", alipayPublicKey, "RSA2");
            AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
            req.setNotifyUrl(notifyUrl);
            req.setReturnUrl(notBlank(backUrl) ? backUrl : returnUrl);
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", outTradeNo);
            biz.put("total_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            biz.put("subject", subject == null ? outTradeNo : subject);
            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
            req.setBizContent(objectMapper.writeValueAsString(biz));
            String url = client.pageExecute(req, "GET").getBody();
            if (url == null || url.isBlank()) {
                throw new BizException("支付宝未返回支付链接");
            }
            return new EscrowStart(false, url);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("支付宝下单失败", e);
            throw new BizException("支付宝下单失败：" + e.getMessage());
        }
    }

    public boolean verify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
        } catch (Exception e) {
            log.warn("支付宝验签失败", e);
            return false;
        }
    }

    public Long parseStageId(String outTradeNo) {
        return parseId(outTradeNo, "ESCROW-");
    }

    public Long parseIntentionId(String outTradeNo) {
        return parseId(outTradeNo, "INTENTION-");
    }

    private static Long parseId(String outTradeNo, String prefix) {
        if (outTradeNo == null || !outTradeNo.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.valueOf(outTradeNo.substring(prefix.length()));
        } catch (Exception e) {
            return null;
        }
    }

    public static String outTradeNo(Long stageId) {
        return "ESCROW-" + stageId;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
