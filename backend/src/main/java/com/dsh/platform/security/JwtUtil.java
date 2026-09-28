package com.dsh.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /** HS256 要求密钥至少 256 bit。 */
    static final int MIN_SECRET_BYTES = 32;

    @Value("${dsh.jwt.secret:}")
    private String secret;

    @Value("${dsh.jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey cachedKey;

    /**
     * 启动即校验：没有配置或长度不足直接拒绝启动，避免带着弱密钥/空密钥上线。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "dsh.jwt.secret 未配置。请在 application-local.yml 或环境变量 DSH_JWT_SECRET 中设置一个至少 32 字节的随机字符串。");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "dsh.jwt.secret 过短（需 ≥ " + MIN_SECRET_BYTES + " 字节）。");
        }
        cachedKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey key() {
        if (cachedKey == null) {
            validateSecret();
        }
        return cachedKey;
    }

    public String generate(Long userId, Long tenantId, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireHours * 3600 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        Object tenantRaw = claims.get("tenantId");
        Long tenantId = tenantRaw instanceof Number n ? n.longValue() : null;
        String role = claims.get("role") == null ? null : String.valueOf(claims.get("role")).trim();
        return new LoginUser(userId, tenantId, role);
    }
}
