package com.ainote.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 层配置：将上传目录映射为静态资源，
 * 使 /files/** 下的文件可直接通过浏览器访问（context-path=/api，即 /api/files/**）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:./upload}")
    private String uploadDir;

    @Value("${file.access-prefix:/files}")
    private String accessPrefix;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(accessPrefix + "/**")
                .addResourceLocations(location);
    }
}