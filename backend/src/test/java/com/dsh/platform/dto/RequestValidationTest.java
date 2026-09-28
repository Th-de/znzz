package com.dsh.platform.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入口 DTO 的形状校验。不启动 Spring，直接用 Bean Validation。
 */
class RequestValidationTest {

    private static final Validator V = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> Set<String> messages(T obj) {
        return V.validate(obj).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("登录：账号与密码都必填")
    void loginRequiresBothFields() {
        assertThat(messages(new AuthDtos.LoginRequest("", null)))
                .containsExactlyInAnyOrder("请填写账号", "请填写密码");
        assertThat(messages(new AuthDtos.LoginRequest("admin", "admin123"))).isEmpty();
    }

    @Test
    @DisplayName("注册：手机号 11 位、密码 ≥6、信用代码 18 位")
    void registerShapeRules() {
        AuthDtos.RegisterRequest bad = new AuthDtos.RegisterRequest(
                "12345", "abc", "", "SHORT", "", "", "");
        assertThat(messages(bad)).contains(
                "手机号必须为11位数字", "密码至少 6 位", "请填写企业名称",
                "请填写18位统一社会信用代码", "请选择企业类型", "请填写联系人", "请填写企业地址");

        AuthDtos.RegisterRequest ok = new AuthDtos.RegisterRequest(
                "13800000001", "secret1", "宁波博锐精密", "91330200MA2H3XYZ7K",
                "FACTORY", "张工", "浙江省宁波市北仑区");
        assertThat(messages(ok)).isEmpty();
    }

    @Test
    @DisplayName("发单：标题/产品/数量必填，数量 ≥1，意向期 1~30 天，分期 1~10")
    void publishShapeRules() {
        DemandDtos.PublishRequest bad = new DemandDtos.PublishRequest(
                " ", null, null, 0,
                null, null, null, null, null, new BigDecimal("-1"), -5,
                null, null, null, null, null, null,
                0, null, null, null, null, null, null, null, null, null,
                11, null);
        assertThat(messages(bad)).contains(
                "请填写需求标题", "请填写产品名称", "数量必须大于 0",
                "最低良率不能为负", "最低信用分不能为负",
                "意向期至少 1 天", "分期交付次数须在 1~10 之间");
    }

    @Test
    @DisplayName("发单：嵌套工序与分期也参与校验")
    void publishValidatesNestedItems() {
        DemandDtos.PublishRequest req = new DemandDtos.PublishRequest(
                "转向节外协", "转向节", null, 500,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                5, null,
                List.of(new DemandDtos.ProcessItem(0, "x".repeat(65), null, null)),
                null, null, null, null, null, null, null,
                2, List.of(new DemandDtos.DeliveryPeriod(150, -1, null, null, null)));
        assertThat(messages(req)).contains("工序序号从 1 开始", "工序名称最多 64 字");
        assertThat(V.validate(req)).anyMatch(v -> v.getPropertyPath().toString().startsWith("deliveryPlan[0].percent"));
    }

    @Test
    @DisplayName("报名：需求 ID 必填，承接量必须为正")
    void intentionShapeRules() {
        assertThat(messages(new BiddingDtos.IntentionRequest(null, 1, 0, -3, null, null)))
                .contains("缺少需求 ID", "最小承接量必须大于 0", "最大承接量必须大于 0");
        assertThat(messages(new BiddingDtos.IntentionRequest(9L, 1, 100, 300, null, null))).isEmpty();
    }

    @Test
    @DisplayName("思考期填报：方案必填、单价列表非空且单价为正")
    void commitShapeRules() {
        assertThat(messages(new BiddingDtos.CommitRequest(9L, "", null, List.of())))
                .contains("请填写实施方案", "请填写该品单价");
        assertThat(messages(new BiddingDtos.CommitRequest(9L, "三轴铣+精车",
                null, List.of(new BiddingDtos.CommitItem(1, new BigDecimal("0"), null, null)))))
                .contains("单价必须大于 0");
        assertThat(messages(new BiddingDtos.CommitRequest(9L, "三轴铣+精车",
                null, List.of(new BiddingDtos.CommitItem(1, new BigDecimal("12.50"), null, null)))))
                .isEmpty();
    }
}
