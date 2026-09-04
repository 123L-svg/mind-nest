package com.ainote.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成与解析访问令牌
 */
@Component
public class JwtUtil {

    private static final String CLAIM_UID = "uid";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-time}")
    private Long expireMillis;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // 配置的 secret 长度不足 32 字节时直接用会抛 WeakKeyException，
        // 统一经 SHA-256 派生为定长 32 字节 HS256 密钥，兼容任意长度配置。
        this.key = Keys.hmacShaKeyFor(
                digest(secret.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] digest(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 密钥派生失败", e);
        }
    }

    /**
     * 生成 token
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_UID, userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，失败返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 从 token 中提取用户ID，无效返回 null
     */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.get(CLAIM_UID) == null) {
            return null;
        }
        return ((Number) claims.get(CLAIM_UID)).longValue();
    }
}