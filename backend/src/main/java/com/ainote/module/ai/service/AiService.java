package com.ainote.module.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.module.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 能力服务（OpenAI 兼容 Chat Completions）
 * 支持四种操作：大纲生成/润色/摘要/问答
 */
@Service
@RequiredArgsConstructor
public class AiService {

    public static final String OUTLINE = "outline";
    public static final String POLISH = "polish";
    public static final String SUMMARIZE = "summarize";
    public static final String CHAT = "chat";

    private final AiProperties props;

    /**
     * 执行 AI 操作，返回文本结果
     */
    public String execute(String action, String title, String content, String question) {
        String system = systemPrompt(action);
        String user = userPrompt(action, title, content, question);
        if (props.isMockEnabled() || !props.isRealReady()) {
            return mock(action, title, content);
        }
        return chatCompletion(props.getModel(), system, user);
    }

    /**
     * 调 OpenAI 兼容 /chat/completions
     */
    private String chatCompletion(String model, String system, String user) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("temperature", 0.6);
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", system));
        messages.add(new JSONObject().set("role", "user").set("content", user));
        body.set("messages", messages);

        String url = StrUtil.removeSuffix(props.getBaseUrl(), "/") + "/chat/completions";
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout((int) props.getTimeoutMillis())
                .execute()) {
            if (!resp.isOk()) {
                throw new BusinessException("AI 调用失败：" + resp.body());
            }
            JSONObject ret = JSONUtil.parseObj(resp.body());
            JSONArray choices = ret.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 返回异常：缺少 choices");
            }
            String content = choices.getJSONObject(0)
                    .getJSONObject("message").getStr("content");
            return StrUtil.cleanBlank(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 调用异常：" + e.getMessage());
        }
    }

    private String systemPrompt(String action) {
        return switch (action) {
            case OUTLINE -> "你是一位资深内容结构化助手。请根据标题和内容生成清晰、分级的笔记大纲，使用 Markdown 列表表达，直接输出大纲正文，不要任何多余说明。";
            case POLISH -> "你是一位专业写作润色助手。请在保留原意的前提下润色以下文字，使表达更通顺、精炼、专业，直接输出润色后全文。";
            case SUMMARIZE -> "你是一位摘要生成助手。请用简洁的一段话概括以下内容的核心要点，直接输出摘要。";
            case CHAT -> "你是该笔记的智能问答助手。请仅依据给定的笔记内容回答用户问题，若内容不含相关信息请如实说明，直接输出回答。";
            default -> "你是一位智能助手，请直接、准确地回答用户。";
        };
    }

    private String userPrompt(String action, String title, String content, String question) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append("笔记标题：").append(title).append("\n");
        }
        if (StrUtil.isNotBlank(content)) {
            sb.append("笔记内容：\n").append(content).append("\n");
        }
        if (!action.equals(CHAT)) {
            sb.append("\n请执行：").append(actionDesc(action)).append("。");
        } else if (StrUtil.isNotBlank(question)) {
            sb.append("\n用户问题：").append(question);
        }
        return sb.toString();
    }

    private String actionDesc(String action) {
        return switch (action) {
            case OUTLINE -> "生成大纲";
            case POLISH -> "润色全文";
            case SUMMARIZE -> "生成摘要";
            default -> "回答该问题";
        };
    }

    /** 未配置 key 时的模拟结果 */
    private String mock(String action, String title, String content) {
        return switch (action) {
            case OUTLINE -> {
                String t = StrUtil.isBlank(title) ? "未命名笔记" : title;
                yield "## " + t + "（大纲）\n- 背景与目标\n- 核心要点\n  - 要点一\n  - 要点二\n  - 要点三\n- 实施步骤\n- 总结与展望";
            }
            case POLISH -> StrUtil.isNotBlank(content)
                    ? "【已润色】" + StrUtil.cleanBlank(content)
                    : "【已润色】请先提供要润色的内容。";
            case SUMMARIZE -> StrUtil.isNotBlank(content)
                    ? "本文概述：" + StrUtil.sub(StrUtil.cleanBlank(content), 0, 80)
                    : "本文暂无内容可供摘要。";
            case CHAT -> "（演示模式）我是该笔记的 AI 助手。接入真实 AI 服务后，将依据笔记内容回答你的问题。";
            default -> "未知操作";
        };
    }
}