package com.ainote.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 接口配置（OpenAI 兼容）
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 接口基础地址，如 https://dashscope.aliyuncs.com/compatible-mode/v1 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名，默认 qwen-plus */
    private String model = "qwen-plus";

    /** 超时（毫秒） */
    private long timeoutMillis = 60000;

    /** 是否启用 mock（未配置 key 时返回模拟结果，便于联调） */
    private boolean mockEnabled = true;

    /** 是否已具备调用真实服务的条件 */
    public boolean isRealReady() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}