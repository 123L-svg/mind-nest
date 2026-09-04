package com.ainote.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 认证辅助：JWT 登出黑名单 + 登录失败限流（防暴力破解）。
 * <p>所有 Redis 操作均 try-catch 降级：Redis 不可用时不影响主流程
 * （黑名单不生效、限流不拦截），保证系统可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    private static final String LOGOUT_KEY = "auth:logout:";
    private static final String FAIL_KEY = "auth:fail:";
    private static final int MAX_FAIL = 5;
    private static final long LOCK_SECONDS = 900; // 15 分钟

    /** 将 token 加入黑名单，TTL 为其剩余有效期（秒） */
    public void blacklist(String token, long ttlSeconds) {
        if (token == null || ttlSeconds <= 0) return;
        try {
            redis.opsForValue().set(LOGOUT_KEY + token, "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 黑名单写入失败：{}", e.getMessage());
        }
    }

    /** 是否已登出（黑名单命中） */
    public boolean isBlacklisted(String token) {
        if (token == null) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(LOGOUT_KEY + token));
        } catch (Exception e) {
            return false;
        }
    }

    /** 该 key 是否已达登录锁定阈值 */
    public boolean isLoginLocked(String key) {
        try {
            String v = redis.opsForValue().get(FAIL_KEY + key);
            return v != null && Integer.parseInt(v) >= MAX_FAIL;
        } catch (Exception e) {
            return false;
        }
    }

    /** 记录一次登录失败并设置首键过期时间 */
    public void recordFail(String key) {
        try {
            Long n = redis.opsForValue().increment(FAIL_KEY + key);
            if (n != null && n == 1L) {
                redis.expire(FAIL_KEY + key, LOCK_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Redis 限流计数失败：{}", e.getMessage());
        }
    }

    /** 登录成功清除失败计数 */
    public void clearFail(String key) {
        try {
            redis.delete(FAIL_KEY + key);
        } catch (Exception e) {
            log.warn("Redis 清除计数失败：{}", e.getMessage());
        }
    }
}