package com.ainote.module.file.store;

import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.InputStream;

/**
 * MinIO 模式下的 /files/** 访问代理：
 * <p>本地模式下由 WebMvcConfig 静态映射直接读取；minio 模式下拦截 /files/{key}
 * 请求，从 MinIO bucket 拉取对象原样返回，使前端图片/附件地址无需任何改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
@ConditionalOnProperty(name = "file.store", havingValue = "minio")
public class MinioFileProxyFilter extends OncePerRequestFilter {

    private final MinioFileStore minioFileStore;

    @Value("${file.access-prefix:/files}")
    private String accessPrefix;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String prefix = request.getContextPath() + (request.getServletPath().isEmpty() ? accessPrefix : accessPrefix);
        // servletPath 已剥离 context-path，直接判断
        return !request.getServletPath().startsWith(accessPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, java.io.IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String servletPath = request.getServletPath();
            String key = servletPath.substring(accessPrefix.length());
            while (key.startsWith("/")) key = key.substring(1);
            if (!key.isEmpty()) {
                try {
                    StatObjectResponse stat = minioFileStore.stat(key);
                    if (stat != null) {
                        try (InputStream in = minioFileStore.get(key)) {
                            if (in != null) {
                                response.setContentType(stat.contentType() == null
                                        ? "application/octet-stream" : stat.contentType());
                                StreamUtils.copy(in, response.getOutputStream());
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("MinIO 读取对象失败 {}：{}", key, e.getMessage());
                }
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}