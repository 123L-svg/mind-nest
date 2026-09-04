package com.ainote.module.note.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记版本（列表项，不含 content）
 */
@Data
public class NoteVersionVO {

    private Long id;

    private Long noteId;

    private String title;

    private String summary;

    private Integer wordCount;

    private LocalDateTime createTime;
}