package com.ainote.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Redis 令牌桶限流。
 * <p>单个限流桶对应一个 Redis Hash：{tokens 当前令牌数, lastRefill 上次补充时间}。
 * 访问时先按流逝时间补充令牌，再判断是否可取；整个过程用 Lua 脚本保证「检查+扣减」的原子性。
 * <p>与 {@code TokenBlacklistService} 一致：Redis 异常时降级放行，不影响主流程可用性。
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> tokenBucketScript;

    /**
     * 令牌桶 Lua 脚本。
     * <ul>
     *   <li>KEYS[1]: 桶 key</li>
     *   <li>ARGV[1]: capacity 桶容量</li>
     *   <li>ARGV[2]: refillTokensPerMs 每毫秒补充的令牌数（= refillPerSecond / 1000）</li>
     *   <li>ARGV[3]: now 当前毫秒时间戳</li>
     *   <li>ARGV[4]: ttl 桶的存活时长（毫秒），按「补满一桶的时间」设定，无流量时到期释放 key</li>
     * </ul>
     * 返回 1 表示获取令牌成功（放行），0 表示桶空（拒绝）。
     */
    private static final String SCRIPT =
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local ratePerMs = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local ttl = tonumber(ARGV[4]) " +
            "local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill') " +
            "local tokens = tonumber(bucket[1]) " +
            "local lastRefill = tonumber(bucket[2]) " +
            "if tokens == nil then tokens = capacity end " +
            "if lastRefill == nil then lastRefill = now end " +
            "local elapsed = now - lastRefill " +
            "if elapsed > 0 then " +
            "    tokens = math.min(capacity, tokens + elapsed * ratePerMs) " +
            "end " +
            "local allowed = 0 " +
            "if tokens >= 1 then " +
            "    tokens = tokens - 1 " +
            "    allowed = 1 " +
            "end " +
            "redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now) " +
            "redis.call('PEXPIRE', key, ttl) " +
            "return allowed";

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
        this.tokenBucketScript = new DefaultRedisScript<>(SCRIPT, Long.class);
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param bucketKey      限流桶标识（已含业务前缀与客户端维度）
     * @param capacity       桶容量
     * @param refillPerSecond 每秒补充令牌数
     * @return true 放行；false 限流拒绝。Redis 异常时降级返回 true
     */
    public boolean tryAcquire(String bucketKey, int capacity, int refillPerSecond) {
        // 防御：补满速率非法时按 1 兜底，避免桶耗尽后永不补充导致接口永久拒绝
        int rate = Math.max(1, refillPerSecond);
        // 桶 TTL = 补满一桶时间 * 2（下限 5s）：保证无流量时保留桶历史，到期才释放 key 空间
        long ttl = Math.max(5000L, (long) capacity * 1000L / rate * 2L);
        long now = System.currentTimeMillis();
        double ratePerMs = rate / 1000.0;
        try {
            Long result = redis.execute(
                    tokenBucketScript,
                    Collections.singletonList(bucketKey),
                    String.valueOf(capacity),
                    String.valueOf(ratePerMs),
                    String.valueOf(now),
                    String.valueOf(ttl));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("Redis 限流判断失败，降级放行：{}", e.getMessage());
            return true;
        }
    }
}