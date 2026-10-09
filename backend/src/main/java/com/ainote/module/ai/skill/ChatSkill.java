package com.ainote.module.ai.skill;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 对话技能：AI 面板多轮对话，支持携带用户补充指令。
 */
@Component
public class ChatSkill implements AiSkill {

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public String description() {
        return "AI 对话（基于笔记上下文）";
    }

    @Override
    public String systemPrompt(JSONObject params) {
        String base = "你是一位专业的笔记助手，可以基于当前笔记内容回答用户问题。";
        if (params != null && StrUtil.isNotBlank(params.getStr("instruction"))) {
            base += "请遵循用户的补充指令：" + params.getStr("instruction") + "。";
        }
        return base;
    }

    @Override
    public String userPrompt(JSONObject params, String title, String content, String question) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append("当前笔记标题：").append(title.trim()).append("\n\n");
        }
        if (StrUtil.isNotBlank(content)) {
            sb.append("当前笔记内容：\n").append(content.trim()).append("\n\n");
        }
        if (StrUtil.isNotBlank(question)) {
            sb.append("用户问题：").append(question.trim());
        }
        return sb.toString();
    }
}
