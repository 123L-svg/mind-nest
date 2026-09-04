package com.ainote.module.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记版本快照（保存时记录，可历史回溯/回滚）
 */
@Data
@TableName("note_version")
public class NoteVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long noteId;

    private String title;

    /** 富文本内容 */
    @TableField("content")
    private String content;

    private String summary;

    private Integer wordCount;

    private LocalDateTime createTime;
}