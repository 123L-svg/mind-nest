package com.ainote.module.file.store;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 文件防盗链：校验 /files/** 请求的 Referer/Origin，拒绝外站嵌入（盗链）。
 * <p>空 Referer（直接访问/图片首次加载）放行；非本站来源拒绝 403。
 */
@Slf4j
@Component
@Order(90)
public class FileHotlinkGuardFilter extends OncePerRequestFilter {

    @Value("${file.access-prefix:/files}")
    private String accessPrefix;

    /** 允许的来源（逗号分隔），默认本站 localhost/127.0.0.1 任意端口 */
    @Value("${file.hotlink-allowed:http://localhost,http://127.0.0.1}")
    private String allowedOrigins;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(accessPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod()) && !isAllowedReferer(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String origin = request.getHeader("Origin");
        String source = StringUtils.hasText(origin) ? origin : referer;
        // 无来源（直接访问 URL / 部分客户端）放行
        if (!StringUtils.hasText(source)) {
            return true;
        }
        try {
            String host = new java.net.URI(source).getHost();
            for (String allowed : allowedOrigins.split(",")) {
                String a = allowed.trim();
                if (!a.isEmpty() && host != null && host.equals(new java.net.URI(a).getHost())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Referer 解析失败：{}", source);
            return false;
        }
        return false;
    }
}