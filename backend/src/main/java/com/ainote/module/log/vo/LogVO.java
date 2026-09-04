package com.ainote.module.log.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志返回
 */
@Data
public class LogVO {

    private Long id;

    private String module;

    private String action;

    private String method;

    private String ip;

    private Long duration;

    private Integer success;

    private String errorMsg;

    private LocalDateTime createTime;
}