package com.ainote.module.note.vo;

import com.ainote.module.tag.vo.TagVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记返回（列表不含 content，详情含 content）
 */
@Data
@Schema(description = "笔记信息")
public class NoteVO {

    @Schema(description = "笔记ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容（详情接口返回）")
    private String content;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "ES 搜索结果高亮片段（含 <em> 标签），非搜索时为空")
    private String highlight;

    @Schema(description = "字数")
    private Integer wordCount;

    @Schema(description = "浏览次数")
    private Integer viewCount;

    @Schema(description = "状态 0正常 1回收站")
    private Integer status;

    @Schema(description = "标签列表")
    private List<TagVO> tags;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}