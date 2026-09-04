package com.ainote.module.oauth.service;

import com.ainote.module.oauth.dto.OAuthLoginDTO;
import com.ainote.module.oauth.vo.OAuthUrlVO;
import com.ainote.module.user.vo.LoginVO;

/**
 * 第三方 OAuth 登录服务。
 */
public interface OAuthService {

    /** 生成第三方授权跳转地址 */
    OAuthUrlVO authorizeUrl(String source);

    /** 用授权码换取登录结果（首次自动创建账号） */
    LoginVO login(OAuthLoginDTO dto);
}