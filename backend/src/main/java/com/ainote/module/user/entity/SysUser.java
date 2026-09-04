package com.ainote.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 第三方来源：github/gitee 等，空表示账号密码用户 */
    private String oauthType;

    /** 第三方平台唯一标识（配合 oauthType 唯一） */
    private String openId;

    /** 账号状态 0正常 1禁用 */
    private Integer status;

    /** 逻辑删除 0否 1是 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}