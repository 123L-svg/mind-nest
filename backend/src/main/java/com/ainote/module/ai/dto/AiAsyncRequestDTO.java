package com.ainote.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 异步任务提交入参（action 决定处理类型）
 */
@Data
public class AiAsyncRequestDTO {

    /** 动作：outline / polish / summarize / chat */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** 笔记标题（大纲生成用） */
    private String title;

    /** 笔记内容 */
    private String content;

    /** 用户问题（问答用） */
    private String question;
}