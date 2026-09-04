package com.ainote.module.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记
 */
@Data
@TableName("note")
public class Note {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long kbId;

    private Long categoryId;

    private String title;

    /** 富文本内容 */
    private String content;

    private String summary;

    private Integer wordCount;

    private Integer viewCount;

    /** 状态 0正常 1回收站 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}