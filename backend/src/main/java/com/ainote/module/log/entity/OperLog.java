package com.ainote.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Data
@TableName("oper_log")
public class OperLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String module;

    private String action;

    private String method;

    private String params;

    private String ip;

    private Long duration;

    /** 是否成功 0否 1是 */
    private Integer success;

    private String errorMsg;

    private LocalDateTime createTime;
}