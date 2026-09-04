package com.ainote.module.user.service;

import com.ainote.module.user.dto.LoginDTO;
import com.ainote.module.user.dto.RegisterDTO;
import com.ainote.module.user.vo.LoginVO;

/**
 * 认证服务
 */
public interface AuthService {

    /** 注册 */
    void register(RegisterDTO dto);

    /** 登录，返回 token */
    LoginVO login(LoginDTO dto);

    /** 登出：将当前 token 加入黑名单，使其立即失效 */
    void logout(String token);
}