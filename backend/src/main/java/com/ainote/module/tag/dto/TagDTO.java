package com.ainote.module.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签请求
 */
@Data
@Schema(description = "标签请求")
public class TagDTO {

    @Schema(description = "编辑时必传的标签ID")
    private Long id;

    @Schema(description = "标签名称")
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 32, message = "名称长度不能超过 32")
    private String name;
}