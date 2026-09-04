package com.ainote.common.util;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类：获取当前登录用户
 * <p>认证过滤器会将当前用户ID（Long）写入 Authentication 的 principal。
 */
public class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 当前登录用户ID，未登录则抛出未认证异常
     */
    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();
    }

    /**
     * 当前登录用户ID；未登录返回 null（用于公开/可选登录接口）
     */
    public static Long getUserIdOrNull() {
        try {
            return getUserId();
        } catch (BusinessException e) {
            return null;
        }
    }
}