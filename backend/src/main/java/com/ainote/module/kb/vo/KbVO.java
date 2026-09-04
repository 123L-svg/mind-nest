package com.ainote.module.kb.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库返回
 */
@Data
@Schema(description = "知识库信息")
public class KbVO {

    @Schema(description = "知识库ID")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "封面图")
    private String cover;

    @Schema(description = "是否公开 0私有 1公开")
    private Integer isPublic;

    @Schema(description = "笔记数")
    private Long noteCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}