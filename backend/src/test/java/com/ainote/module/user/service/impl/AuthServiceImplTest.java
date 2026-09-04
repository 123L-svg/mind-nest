package com.ainote.module.user.service.impl;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.redis.TokenBlacklistService;
import com.ainote.common.util.JwtUtil;
import com.ainote.module.user.dto.LoginDTO;
import com.ainote.module.user.dto.RegisterDTO;
import com.ainote.module.user.entity.SysUser;
import com.ainote.module.user.mapper.SysUserMapper;
import com.ainote.module.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务：注册 / 登录 / 登出黑名单 / 限流
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(sysUserMapper, encoder, jwtUtil, tokenBlacklistService);
    }

    @Test
    void registerSucceedsWhenUsernameFree() {
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("123456");
        dto.setNickname("新用户");

        authService.register(dto);

        verify(sysUserMapper).insert(any(SysUser.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("exist");
        dto.setPassword("123456");

        assertThrows(BusinessException.class, () -> authService.register(dto));
    }

    @Test
    void loginSucceedsAndClearsLock() {
        SysUser user = newUser();
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(jwtUtil.generateToken(eq(user.getId()), anyString())).thenReturn("jwt-token");

        LoginVO vo = authService.login(loginDto("tester", "123456"));

        assertNotNull(vo.getToken());
        verify(tokenBlacklistService).clearFail("tester");
    }

    @Test
    void loginWrongPasswordCountsFail() {
        SysUser user = newUser();
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class, () -> authService.login(loginDto("tester", "wrong")));
        verify(tokenBlacklistService).recordFail("tester");
    }

    @Test
    void loginBlockedAfterTooManyFails() {
        when(tokenBlacklistService.isLoginLocked("tester")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDto("tester", "123456")));
        assertTrue(ex.getMessage().contains("失败次数过多"));
    }

    @Test
    void loginRejectsDisabledAccount() {
        SysUser user = newUser();
        user.setStatus(1);
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class, () -> authService.login(loginDto("tester", "123456")));
    }

    private SysUser newUser() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("tester");
        u.setPassword(encoder.encode("123456"));
        u.setNickname("测试");
        u.setStatus(0);
        return u;
    }

    private LoginDTO loginDto(String name, String pwd) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(name);
        dto.setPassword(pwd);
        return dto;
    }
}