package com.ainote.module.tag.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 标签返回
 */
@Data
@Schema(description = "标签信息")
public class TagVO {

    @Schema(description = "标签ID")
    private Long id;

    @Schema(description = "标签名称")
    private String name;
}