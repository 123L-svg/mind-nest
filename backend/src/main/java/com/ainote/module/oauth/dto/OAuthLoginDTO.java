package com.ainote.module.oauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 第三方登录回调参数。
 */
@Data
public class OAuthLoginDTO {

    /** 平台：github | gitee */
    @NotBlank(message = "平台不能为空")
    private String source;

    /** 授权码 */
    @NotBlank(message = "授权码不能为空")
    private String code;

    /** 防 CSRF 状态码 */
    private String state;
}