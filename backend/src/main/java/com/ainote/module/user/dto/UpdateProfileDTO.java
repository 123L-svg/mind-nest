package com.ainote.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求
 */
@Data
@Schema(description = "更新资料请求")
public class UpdateProfileDTO {

    @Schema(description = "昵称")
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 64, message = "邮箱长度不能超过 64")
    private String email;

    @Schema(description = "头像（上传后的访问路径，如 /files/20260827/xxx.png）")
    @Size(max = 255, message = "头像路径长度不能超过 255")
    private String avatar;
}