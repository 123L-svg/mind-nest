package com.ainote.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 令牌桶，按客户端 IP 维度限流。
 * <p>标注在 Controller 方法上，配合 {@link RateLimitAspect} 生效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 业务标识前缀，用于区分不同接口的限流桶（例如 public、file、ai） */
    String key() default "";

    /** 桶容量：允许的瞬时突发请求数（令牌桶最大存量） */
    int capacity() default 5;

    /** 每秒补充的令牌数：决定接口的平均访问速率 */
    int refillPerSecond() default 1;

    /** 触发限流时的提示语 */
    String message() default "请求过于频繁，请稍后再试";
}