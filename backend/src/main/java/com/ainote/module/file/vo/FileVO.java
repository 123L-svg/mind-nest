package com.ainote.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件返回信息
 */
@Data
@Schema(description = "文件信息")
public class FileVO {

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "关联笔记ID")
    private Long noteId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "访问路径")
    private String path;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "文件类型 image/file")
    private String type;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}