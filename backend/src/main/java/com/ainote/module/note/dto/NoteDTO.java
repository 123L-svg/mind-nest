package com.ainote.module.note.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 笔记新增/编辑请求
 */
@Data
@Schema(description = "笔记请求")
public class NoteDTO {

    @Schema(description = "编辑时必传的笔记ID")
    private Long id;

    @Schema(description = "所属知识库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属知识库不能为空")
    private Long kbId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标题")
    @NotBlank(message = "笔记标题不能为空")
    @Size(max = 128, message = "标题长度不能超过 128")
    private String title;

    @Schema(description = "富文本内容")
    private String content;

    @Schema(description = "摘要")
    @Size(max = 512, message = "摘要长度不能超过 512")
    private String summary;

    @Schema(description = "字数")
    private Integer wordCount;

    @Schema(description = "标签ID列表")
    private List<Long> tagIds;
}