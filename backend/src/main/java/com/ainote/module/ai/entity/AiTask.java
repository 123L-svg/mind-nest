package com.ainote.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 异步任务：生产者入队后由 MQ 消费者异步执行并回写结果。
 * <p>status：0 待处理 / 1 处理中 / 2 成功 / 3 失败</p>
 */
@Data
@TableName("ai_task")
public class AiTask {

    /** 状态常量 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;

    /** 任务ID（雪花，对外即 taskId） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 笔记ID（chat 多轮记忆按笔记隔离；可为空） */
    private Long noteId;

    /** 动作：outline / polish / summarize / chat */
    private String action;

    private String title;

    private String content;

    private String question;

    /** 技能参数（JSON 字符串） */
    private String params;

    /** 0待处理 1处理中 2成功 3失败 */
    private Integer status;

    private String result;

    private String errorMsg;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}