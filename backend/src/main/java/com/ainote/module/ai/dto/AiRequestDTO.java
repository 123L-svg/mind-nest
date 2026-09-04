package com.ainote.module.ai.dto;

import lombok.Data;

/**
 * AI 请求统一入参（action 由后端 Controller 按接口确定，无需前端传）
 */
@Data
public class AiRequestDTO {

    /** 笔记标题（大纲生成用） */
    private String title;

    /** 笔记内容 */
    private String content;

    /** 用户问题（问答用） */
    private String question;
}