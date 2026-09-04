package com.ainote.module.stats.vo;

import lombok.Data;

/**
 * 分类占比统计
 */
@Data
public class CategoryStatVO {

    /** 分类ID；null 表示未分类 */
    private Long categoryId;

    /** 分类名称；未分类时为「未分类」 */
    private String categoryName;

    /** 该分类下正常笔记数 */
    private Long noteCount;
}