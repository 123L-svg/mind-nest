package com.ainote.module.stats.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门笔记（按浏览量）
 */
@Data
public class HotNoteVO {

    private Long id;

    private String title;

    private Integer viewCount;

    /** 最近更新时间（用于榜单日期展示） */
    private LocalDateTime updateTime;
}