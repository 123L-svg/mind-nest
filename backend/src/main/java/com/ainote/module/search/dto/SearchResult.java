package com.ainote.module.search.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.Map;

/**
 * ES 搜索结果：笔记 id 分页 + id → 高亮片段（已含 &lt;em&gt; 标签）。
 */
@Data
public class SearchResult {

    /** 命中笔记 id 分页（按相关度顺序） */
    private Page<Long> idPage;

    /** noteId → 高亮片段（title 优先，其次 summary） */
    private Map<Long, String> highlights;

    public SearchResult(Page<Long> idPage, Map<Long, String> highlights) {
        this.idPage = idPage;
        this.highlights = highlights;
    }
}