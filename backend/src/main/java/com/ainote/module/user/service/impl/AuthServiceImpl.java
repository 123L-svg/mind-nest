package com.ainote.module.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.redis.TokenBlacklistService;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.JwtUtil;
import com.ainote.module.user.dto.LoginDTO;
import com.ainote.module.user.dto.RegisterDTO;
import com.ainote.module.user.entity.SysUser;
import com.ainote.module.user.mapper.SysUserMapper;
import com.ainote.module.user.service.AuthService;
import com.ainote.module.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void register(RegisterDTO dto) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.blankToDefault(dto.getNickname(), dto.getUsername()));
        user.setStatus(0);
        sysUserMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 登录失败限流：同一用户名连续失败达阈值后锁定
        String lockKey = dto.getUsername();
        if (tokenBlacklistService.isLoginLocked(lockKey)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "失败次数过多，请 15 分钟后再试");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            tokenBlacklistService.recordFail(lockKey);
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        tokenBlacklistService.clearFail(lockKey);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isBlank(token)) return;
        Claims claims = jwtUtil.parse(token);
        if (claims == null) return;
        long ttlSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        tokenBlacklistService.blacklist(token, ttlSeconds);
    }
}