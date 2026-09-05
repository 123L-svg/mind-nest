package com.ainote.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * SpringDoc OpenAPI 配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(@Value("${knife4j-describe.version:0.0.1}") String version) {
        String schemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("MindNest（智巢）· AI 智能笔记与知识库系统 API")
                        .description("笔记管理、知识库分类、AI 辅助创作")
                        .version(version)
                        .contact(new Contact().name("mind-nest-team")))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}