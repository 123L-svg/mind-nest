package com.ainote.module.ai.skill;

import cn.hutool.json.JSONObject;

/**
 * AI 技能接口：每种 AI 能力封装为独立的技能实现（大纲/润色/摘要/问答）。
 * <p>技能只负责提示词构建与结果后处理，执行编排统一由 {@link AiService} 调度，
 * 通过 {@link AiSkillRegistry} 按 action 名称分发。</p>
 */
public interface AiSkill {

    /**
     * 技能名称，对应请求中的 action 字段（如 outline/polish/summarize/chat）。
     */
    String name();

    /**
     * 技能描述。
     */
    String description();

    /**
     * 构建 System Prompt。
     *
     * @param params 技能参数（可为空，各技能内部处理默认值）
     */
    String systemPrompt(JSONObject params);

    /**
     * 构建 User Prompt。
     *
     * @param params   技能参数
     * @param title    笔记标题（可为空）
     * @param content  笔记内容（可为空）
     * @param question 用户问题（仅 chat 技能使用，其他技能可为空）
     */
    String userPrompt(JSONObject params, String title, String content, String question);

    /**
     * 结果后处理（默认直接返回原文）。
     *
     * @param rawResult AI 返回的原始结果
     * @param params    技能参数
     */
    default String postProcess(String rawResult, JSONObject params) {
        return rawResult;
    }
}
