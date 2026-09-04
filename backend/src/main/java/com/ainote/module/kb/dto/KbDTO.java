package com.ainote.module.kb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库新增/编辑请求
 */
@Data
@Schema(description = "知识库请求")
public class KbDTO {

    @Schema(description = "编辑时必传的知识库ID")
    private Long id;

    @Schema(description = "知识库名称")
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "名称长度不能超过 64")
    private String name;

    @Schema(description = "描述")
    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    @Schema(description = "封面图")
    private String cover;

    @Schema(description = "是否公开 0私有 1公开")
    private Integer isPublic;
}