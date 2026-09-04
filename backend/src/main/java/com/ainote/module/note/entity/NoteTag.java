package com.ainote.module.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 笔记-标签关联
 */
@Data
@TableName("note_tag")
public class NoteTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long noteId;

    private Long tagId;

    @TableLogic
    private Integer deleted;
}