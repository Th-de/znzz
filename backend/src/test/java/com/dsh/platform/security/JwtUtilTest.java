package com.dsh.platform.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String STRONG = "unit-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz";

    private static JwtUtil withSecret(String secret) {
        JwtUtil u = new JwtUtil();
        ReflectionTestUtils.setField(u, "secret", secret);
        ReflectionTestUtils.setField(u, "expireHours", 1L);
        return u;
    }

    @Test
    @DisplayName("空密钥启动即失败")
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> withSecret("").validateSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dsh.jwt.secret");
    }

    @Test
    @DisplayName("短于 32 字节的密钥被拒绝")
    void shortSecretRejected() {
        assertThatThrownBy(() -> withSecret("too-short").validateSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("过短");
    }

    @Test
    @DisplayName("签发后能解析回同样的用户/租户/角色")
    void roundTrip() {
        JwtUtil u = withSecret(STRONG);
        u.validateSecret();

        String token = u.generate(42L, 7L, "FACTORY");
        LoginUser user = u.parse(token);

        assertThat(user.getUserId()).isEqualTo(42L);
        assertThat(user.getTenantId()).isEqualTo(7L);
        assertThat(user.getRole()).isEqualTo("FACTORY");
    }

    @Test
    @DisplayName("用另一把密钥签的 token 解析失败")
    void tokenFromOtherKeyRejected() {
        JwtUtil a = withSecret(STRONG);
        a.validateSecret();
        JwtUtil b = withSecret("another-secret-9876543210-zyxwvutsrqponmlkjihgfedcba");
        b.validateSecret();

        String token = a.generate(1L, 1L, "BUYER");

        assertThatThrownBy(() -> b.parse(token)).isInstanceOf(JwtException.class);
    }
}
