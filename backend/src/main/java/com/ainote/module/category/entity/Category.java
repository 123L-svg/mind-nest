package com.ainote.module.category.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类（挂在知识库下）
 */
@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long kbId;

    private String name;

    private Integer sort;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}