package com.ainote.module.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/编辑请求
 */
@Data
@Schema(description = "分类请求")
public class CategoryDTO {

    @Schema(description = "编辑时必传的分类ID")
    private Long id;

    @Schema(description = "所属知识库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属知识库不能为空")
    private Long kbId;

    @Schema(description = "分类名称")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "名称长度不能超过 64")
    private String name;

    @Schema(description = "排序")
    private Integer sort;
}