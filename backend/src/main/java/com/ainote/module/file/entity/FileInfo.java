package com.ainote.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息
 */
@Data
@TableName("file_info")
public class FileInfo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long noteId;

    /** 原始文件名（表列为驼峰 originalName） */
    @TableField("originalName")
    private String originalName;

    /** 存储文件名（表列为驼峰 storeName） */
    @TableField("storeName")
    private String storeName;

    /** 访问路径 */
    private String path;

    /** 文件大小（字节） */
    private Long size;

    /** 文件类型 image/file */
    private String type;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}