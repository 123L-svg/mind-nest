package com.ainote.module.ai.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI 同步请求入参（action 由后端 Controller 按接口确定，无需前端传）
 */
@Data
public class AiRequestDTO {

    /** 笔记ID（chat 多轮记忆按笔记隔离；新建未保存时为空） */
    private Long noteId;

    /** 笔记标题（大纲生成用） */
    private String title;

    /** 笔记内容 */
    private String content;

    /** 用户问题（问答用） */
    private String question;

    /** 技能参数（Map 形式，可空） */
    private Map<String, Object> params;
}