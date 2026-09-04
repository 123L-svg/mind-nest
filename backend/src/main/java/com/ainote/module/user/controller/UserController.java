package com.ainote.module.user.controller;

import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.Result;
import com.ainote.common.result.ResultCode;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.user.dto.UpdatePasswordDTO;
import com.ainote.module.user.dto.UpdateProfileDTO;
import com.ainote.module.user.entity.SysUser;
import com.ainote.module.user.mapper.SysUserMapper;
import com.ainote.module.user.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息接口
 */
@Tag(name = "用户模块")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    private SysUser currentUser() {
        SysUser user = sysUserMapper.selectById(SecurityUtil.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> getInfo() {
        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(currentUser(), vo);
        return Result.success(vo);
    }

    @Operation(summary = "更新用户资料")
    @PutMapping("/update")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        SysUser user = currentUser();
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        sysUserMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        return Result.success();
    }
}