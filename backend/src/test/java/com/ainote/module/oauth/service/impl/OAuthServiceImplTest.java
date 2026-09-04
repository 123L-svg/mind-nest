package com.ainote.module.oauth.service.impl;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.util.JwtUtil;
import com.ainote.module.oauth.config.OAuthProperties;
import com.ainote.module.oauth.dto.OAuthLoginDTO;
import com.ainote.module.oauth.vo.OAuthUrlVO;
import com.ainote.module.user.entity.SysUser;
import com.ainote.module.user.mapper.SysUserMapper;
import com.ainote.module.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第三方 OAuth 登录：授权地址生成、state 存储、首次自动建号、二次复用、禁用拦截。
 * <p>resolveLogin 免网络（不回退 code），可直接 mock 用户查询/写入验证账号逻辑。
 */
@ExtendWith(MockitoExtension.class)
class OAuthServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private OAuthProperties properties;
    private OAuthServiceImpl oauthService;

    @BeforeEach
    void setUp() {
        properties = new OAuthProperties();
        properties.setEnabled(true);
        OAuthProperties.Provider github = new OAuthProperties.Provider();
        github.setClientId("cid");
        github.setClientSecret("secret");
        github.setRedirectUri("http://localhost:5174/oauth/callback");
        Map<String, OAuthProperties.Provider> providers = new HashMap<>();
        providers.put("github", github);
        properties.setProviders(providers);

        oauthService = new OAuthServiceImpl(properties, sysUserMapper, jwtUtil, redis);
    }

    private OAuthLoginDTO dto(String source, String code, String state) {
        OAuthLoginDTO d = new OAuthLoginDTO();
        d.setSource(source);
        d.setCode(code);
        d.setState(state);
        return d;
    }

    @Test
    void authorizeUrlReturnsUrlAndStoresState() {
        when(redis.opsForValue()).thenReturn(valueOps);

        OAuthUrlVO vo = oauthService.authorizeUrl("GitHub");

        assertNotNull(vo.getUrl());
        assertNotNull(vo.getState());
        assertTrue(vo.getUrl().contains("cid"));
        assertTrue(vo.getUrl().contains("localhost:5174"));
        verify(valueOps).set(anyString(), eq("GitHub"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void authorizeUrlRejectedWhenDisabled() {
        properties.setEnabled(false);

        assertThrows(BusinessException.class, () -> oauthService.authorizeUrl("github"));
    }

    @Test
    void authorizeUrlRejectedWhenProviderUnconfigured() {
        properties.setProviders(new HashMap<>());

        assertThrows(BusinessException.class, () -> oauthService.authorizeUrl("gitee"));
    }

    @Test
    void resolveLoginCreatesAccountOnFirstEntry() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(jwtUtil.generateToken(any(), anyString())).thenReturn("jwt-token");

        LoginVO vo = oauthService.resolveLogin("GitHub", authUser("100", "gh-user", "http://a.png"));

        assertNotNull(vo.getToken());
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser created = captor.getValue();
        assertEquals("github", created.getOauthType());
        assertEquals("100", created.getOpenId());
        assertEquals("gh-user", created.getNickname());
        assertTrue(created.getUsername().startsWith("github_"));
        assertTrue(created.getUsername().length() <= 32);
    }

    @Test
    void resolveLoginReusesExistingAccount() {
        SysUser exist = new SysUser();
        exist.setId(7L);
        exist.setUsername("github_7");
        exist.setNickname("已有用户");
        exist.setStatus(0);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(exist);
        when(jwtUtil.generateToken(any(), anyString())).thenReturn("jwt-token");

        LoginVO vo = oauthService.resolveLogin("github", authUser("7", "gh", null));

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        assertEquals("已有用户", vo.getNickname());
        assertEquals(7L, vo.getUserId());
    }

    @Test
    void resolveLoginRejectsDisabledAccount() {
        SysUser exist = new SysUser();
        exist.setId(7L);
        exist.setUsername("github_7");
        exist.setStatus(1);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(exist);

        assertThrows(BusinessException.class,
                () -> oauthService.resolveLogin("github", authUser("7", "gh", null)));
    }

    @Test
    void resolveLoginRejectsMissingOpenId() {
        AuthUser user = AuthUser.builder().nickname("no-uuid").build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> oauthService.resolveLogin("github", user));
        assertTrue(ex.getMessage().contains("账号标识缺失"));
    }

    @Test
    void loginRejectsWhenStateMismatch() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // Redis 中 state 对应的是 gitee，而请求声称 github → 不匹配
        when(valueOps.get(anyString())).thenReturn("gitee");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> oauthService.login(dto("github", "code", "some-state")));
        assertTrue(ex.getMessage().contains("授权状态校验失败"));
    }

    @Test
    void loginRejectedWhenDisabledBeforeNetworkCall() {
        properties.setEnabled(false);

        assertThrows(BusinessException.class,
                () -> oauthService.login(dto("github", "code", "state")));
    }

    private AuthUser authUser(String uuid, String nickname, String avatar) {
        return AuthUser.builder().uuid(uuid).nickname(nickname).avatar(avatar).build();
    }
}