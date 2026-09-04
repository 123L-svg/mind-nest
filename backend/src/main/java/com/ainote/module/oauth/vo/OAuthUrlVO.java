package com.ainote.module.oauth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 第三方授权跳转地址（含 state）。
 */
@Data
@AllArgsConstructor
public class OAuthUrlVO {

    /** 需跳转到第三方平台的授权地址 */
    private String url;

    /** 防 CSRF 状态码（授权后原样回传，前端需回传后端） */
    private String state;
}