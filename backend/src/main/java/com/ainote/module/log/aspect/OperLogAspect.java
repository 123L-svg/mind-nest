package com.ainote.module.log.aspect;

import cn.hutool.json.JSONUtil;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.log.annotation.OperLog;
import com.ainote.module.log.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 记录标注了 @OperLog 的方法调用日志
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperLogService operLogService;

    @Around("@annotation(com.ainote.module.log.annotation.OperLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            error = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            com.ainote.module.log.entity.OperLog log = buildLog(joinPoint, duration, success, error);
            operLogService.save(log);
        }
    }

    private com.ainote.module.log.entity.OperLog buildLog(ProceedingJoinPoint joinPoint, long duration,
                             boolean success, Throwable error) {
        com.ainote.module.log.entity.OperLog log = new com.ainote.module.log.entity.OperLog();
        log.setUserId(SecurityUtil.getUserIdOrNull());
        log.setDuration(duration);
        log.setSuccess(success ? 1 : 0);
        if (error != null) {
            log.setErrorMsg(error.getMessage());
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperLog annotation = method.getAnnotation(OperLog.class);
        if (annotation != null) {
            log.setAction(annotation.action());
            log.setModule(annotation.module());
        }
        log.setMethod(signature.getDeclaringType().getSimpleName() + "." + method.getName());

        // 参数（过滤文件，避免过大）
        Object[] args = joinPoint.getArgs();
        Object[] safeArgs = Arrays.stream(args)
                .filter(a -> !(a instanceof MultipartFile))
                .toArray();
        try {
            log.setParams(JSONUtil.toJsonStr(safeArgs).length() > 2000
                    ? JSONUtil.toJsonStr(safeArgs).substring(0, 2000) : JSONUtil.toJsonStr(safeArgs));
        } catch (Exception e) {
            log.setParams("参数序列化失败");
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            log.setIp(resolveIp(request));
        }
        return log;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}