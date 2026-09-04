package com.ainote.module.stats.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据统计
 */
@Data
public class StatsVO {

    private Long kbCount;

    private Long noteCount;

    private Long recycleCount;

    private Long totalViewCount;

    /** 最近编辑的笔记标题 */
    private String recentTitle;

    private LocalDateTime recentUpdateTime;

    /** 近 30 日新增笔记趋势 */
    private List<TrendVO> trend;

    /** 热门笔记 TOP5（按浏览量） */
    private List<HotNoteVO> hotNotes;

    /** 分类占比 */
    private List<CategoryStatVO> categoryStats;
}