package com.ainote.module.ai.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 异步任务消息体（经 RabbitMQ 传输，生产、消费者使用同一结构）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAsyncMessage implements Serializable {

    /** 任务ID，消费者据此回写结果（天然唯一，兼作幂等键） */
    private Long taskId;

    private Long userId;
    private String action;
    private String title;
    private String content;
    private String question;
    private String params;
}