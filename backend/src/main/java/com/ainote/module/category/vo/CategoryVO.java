package com.ainote.module.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类返回
 */
@Data
@Schema(description = "分类信息")
public class CategoryVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "排序")
    private Integer sort;
}