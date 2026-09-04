package com.ainote.module.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方 OAuth 登录配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 各平台配置（key: github/gitee） */
    private Map<String, Provider> providers = new HashMap<>();

    @Data
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}