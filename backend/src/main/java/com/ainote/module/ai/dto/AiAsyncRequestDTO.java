package com.ainote.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * AI 异步任务提交入参（action 决定处理类型）
 */
@Data
public class AiAsyncRequestDTO {

    /** 动作：outline / polish / summarize / chat */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** 笔记ID（chat 多轮记忆按笔记隔离；新建未保存时为空） */
    private Long noteId;

    /** 笔记标题 */
    private String title;

    /** 笔记内容 */
    private String content;

    /** 用户问题 */
    private String question;

    /**
     * 技能参数（Map 形式，可空。不同技能支持的参数不同：
     * <ul>
     *   <li>outline: {type: organize/creative, levels: 2/3, withPoints: true/false}</li>
     *   <li>polish: {style: concise/formal/vivid/academic/casual, intensity: light/medium/heavy}</li>
     *   <li>summarize: {length: short/medium/long, keywords: "关键词1,关键词2"}</li>
     * </ul>
     */
    private Map<String, Object> params;

}