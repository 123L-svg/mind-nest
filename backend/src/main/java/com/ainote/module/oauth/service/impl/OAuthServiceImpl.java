package com.ainote.module.oauth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.JwtUtil;
import com.ainote.module.oauth.config.OAuthProperties;
import com.ainote.module.oauth.dto.OAuthLoginDTO;
import com.ainote.module.oauth.service.OAuthService;
import com.ainote.module.oauth.vo.OAuthUrlVO;
import com.ainote.module.user.entity.SysUser;
import com.ainote.module.user.mapper.SysUserMapper;
import com.ainote.module.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 第三方 OAuth 登录实现（GitHub / Gitee）。
 * <p>state 存 Redis 校验 CSRF，Redis 不可用时降级放行，保证可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private final OAuthProperties oauthProperties;
    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    private static final String STATE_KEY = "auth:oauth:state:";
    private static final long STATE_TTL_SECONDS = 300;

    @Override
    public OAuthUrlVO authorizeUrl(String source) {
        checkEnabled();
        AuthRequest request = buildRequest(source);
        String state = UUID.randomUUID().toString().replace("-", "");
        storeState(state, source);
        String url = request.authorize(state);
        return new OAuthUrlVO(url, state);
    }

    @Override
    public LoginVO login(OAuthLoginDTO dto) {
        checkEnabled();
        if (!validState(dto.getState(), dto.getSource())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "授权状态校验失败，请重新登录");
        }
        AuthRequest request = buildRequest(dto.getSource());

        AuthCallback callback = new AuthCallback();
        callback.setCode(dto.getCode());
        callback.setState(dto.getState());
        AuthResponse<AuthUser> response = request.login(callback);
        if (response == null || !response.ok() || response.getData() == null) {
            throw new BusinessException("第三方授权失败：" + (response == null ? "无响应" : response.getMsg()));
        }
        return resolveLogin(dto.getSource(), response.getData());
    }

    /**
     * 授权成功后查/建本地账号并签发 token。
     * <p>包内可见，便于单测在免网络（不换取 code）的前提下验证账号逻辑。
     */
    LoginVO resolveLogin(String source, AuthUser authUser) {
        String openId = authUser.getUuid();
        if (StrUtil.isBlank(openId)) {
            throw new BusinessException("第三方账号标识缺失，登录失败");
        }
        String sourceLc = source.toLowerCase();

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getOauthType, sourceLc)
                        .eq(SysUser::getOpenId, openId)
                        .last("LIMIT 1"));
        if (user == null) {
            user = createUser(sourceLc, openId, authUser);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    private void checkEnabled() {
        if (!oauthProperties.isEnabled()) {
            throw new BusinessException("第三方登录未启用");
        }
    }

    /** 按平台构造 JustAuth 请求（仅支持已配置且已开启的平台） */
    private AuthRequest buildRequest(String source) {
        String s = source.toLowerCase();
        OAuthProperties.Provider p = oauthProperties.getProviders().get(s);
        if (p == null || StrUtil.isBlank(p.getClientId()) || StrUtil.isBlank(p.getClientSecret())) {
            throw new BusinessException("第三方平台 [ " + source + " ] 未配置，请联系管理员");
        }
        // 占位配置（yml 里未替换的 mock 默认值）判定为未接入，
        // 直接返回明确提示，避免跳到 GitHub/Gitee 的 404 页面
        if (p.getClientId().startsWith("mock-")) {
            throw new BusinessException(platformName(s) + " 登录尚未接入：请先在其开放平台创建 OAuth 应用"
                    + "（回调地址 " + p.getRedirectUri() + "），再配置 OAUTH_" + s.toUpperCase()
                    + "_* 环境变量并重启后端");
        }
        AuthConfig config = AuthConfig.builder()
                .clientId(p.getClientId())
                .clientSecret(p.getClientSecret())
                .redirectUri(p.getRedirectUri())
                .build();
        return switch (s) {
            case "github" -> new AuthGithubRequest(config);
            case "gitee" -> new AuthGiteeRequest(config);
            default -> throw new BusinessException("不支持的第三方平台：" + source);
        };
    }

    /** 平台显示名（用于用户提示） */
    private String platformName(String s) {
        return "github".equals(s) ? "GitHub" : "gitee".equals(s) ? "Gitee" : s;
    }

    /** 首次第三方登录：自动创建本地账号（password 为空，仅可通过第三方登录） */
    private SysUser createUser(String source, String openId, AuthUser authUser) {
        SysUser user = new SysUser();
        user.setUsername(buildUsername(source, openId));
        user.setNickname(StrUtil.blankToDefault(authUser.getNickname(),
                StrUtil.blankToDefault(authUser.getUsername(), source)));
        user.setAvatar(authUser.getAvatar());
        user.setOauthType(source);
        user.setOpenId(openId);
        user.setStatus(0);
        sysUserMapper.insert(user);
        return user;
    }

    /** 生成唯一且不超过 32 字符的用户名：{source}_{openId} */
    private String buildUsername(String source, String openId) {
        String prefix = source + "_";
        int maxOpenLen = 32 - prefix.length();
        String open = openId;
        if (open.length() > maxOpenLen) {
            open = open.substring(open.length() - maxOpenLen);
        }
        return prefix + open;
    }

    private void storeState(String state, String source) {
        try {
            redis.opsForValue().set(STATE_KEY + state, source, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("OAuth state 写入 Redis 失败，跳过 state 校验：{}", e.getMessage());
        }
    }

    /** 校验 state 且与平台匹配；Redis 不可用时降级放行 */
    private boolean validState(String state, String source) {
        if (StrUtil.isBlank(state)) {
            return false;
        }
        try {
            String saved = redis.opsForValue().get(STATE_KEY + state);
            redis.delete(STATE_KEY + state);
            if (saved == null) {
                return false;
            }
            return saved.equalsIgnoreCase(source);
        } catch (Exception e) {
            log.warn("OAuth state 校验失败，降级放行：{}", e.getMessage());
            return true;
        }
    }
}