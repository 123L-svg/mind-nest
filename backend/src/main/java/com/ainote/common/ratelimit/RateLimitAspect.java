package com.ainote.common.ratelimit;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面：拦截标注了 {@link RateLimit} 的方法，按客户端 IP 进行令牌桶限流。
 * <p>@Order 置前，保证限流先于操作日志等切面执行；触发限流时抛出 429 业务异常。
 * <p>切点与 {@link com.ainote.module.log.aspect.OperLogAspect} 风格一致：用全限定类名切点 + 签名重取注解，
 * 不依赖方法参数名绑定，运行时更稳健。
 */
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Around("@annotation(com.ainote.common.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit rateLimit = resolveAnnotation(joinPoint);
        String bucketKey = buildBucketKey(joinPoint, rateLimit);
        if (!rateLimitService.tryAcquire(bucketKey, rateLimit.capacity(), rateLimit.refillPerSecond())) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private RateLimit resolveAnnotation(ProceedingJoinPoint joinPoint) {
        // 切点仅匹配方法级 @RateLimit，此处必然存在
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            throw new IllegalStateException("方法缺少 @RateLimit 注解");
        }
        return rateLimit;
    }

    /**
     * 构造限流桶 key：rl:{注解 key}:{客户端 IP}，同一 IP 共享一个桶。
     */
    private String buildBucketKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String prefix = rateLimit.key().isBlank()
                ? signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName()
                : rateLimit.key();
        return "rl:" + prefix + ":" + resolveIp();
    }

    private String resolveIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}