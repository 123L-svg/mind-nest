package com.ainote.module.stats.vo;

import lombok.Data;

/**
 * 近 N 日笔记新增趋势（单日）
 */
@Data
public class TrendVO {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日新增笔记数 */
    private Long count;
}