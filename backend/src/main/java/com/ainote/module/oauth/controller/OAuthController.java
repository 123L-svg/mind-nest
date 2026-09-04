package com.ainote.module.oauth.controller;

import com.ainote.common.result.Result;
import com.ainote.module.oauth.dto.OAuthLoginDTO;
import com.ainote.module.oauth.service.OAuthService;
import com.ainote.module.oauth.vo.OAuthUrlVO;
import com.ainote.module.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方 OAuth 登录接口（路径已含 /auth，属公开路径）。
 */
@Tag(name = "认证模块")
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    @Operation(summary = "获取第三方授权跳转地址")
    @GetMapping("/url")
    public Result<OAuthUrlVO> authorizeUrl(@RequestParam("source") String source) {
        return Result.success(oauthService.authorizeUrl(source));
    }

    @Operation(summary = "第三方授权回调登录（首次自动注册）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody OAuthLoginDTO dto) {
        return Result.success(oauthService.login(dto));
    }
}