package com.ainote.module.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.module.ai.config.AiProperties;
import com.ainote.module.ai.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 能力服务（OpenAI 兼容 Chat Completions）
 * 支持四种操作：大纲生成/润色/摘要/问答
 * <p>chat 为多轮对话：从 Redis 读取该用户的历史上下文一并提交给大模型，
 * 成功后把本轮问答写回 {@link AiChatMemoryService}。
 */
@Service
@RequiredArgsConstructor
public class AiService {

    public static final String OUTLINE = "outline";
    public static final String POLISH = "polish";
    public static final String SUMMARIZE = "summarize";
    public static final String CHAT = "chat";

    private final AiProperties props;
    private final AiChatMemoryService chatMemory;

    /**
     * 执行 AI 操作，返回文本结果
     *
     * @param userId 当前用户ID（用于 chat 的多轮记忆；可空则不带记忆）
     * @param noteId 笔记ID（chat 多轮记忆按笔记隔离；可空则使用草稿桶）
     */
    public String execute(Long userId, Long noteId, String action,
                          String title, String content, String question) {
        String system = systemPrompt(action);
        String user = userPrompt(action, title, content, question);
        if (props.isMockEnabled() || !props.isRealReady()) {
            return mock(action, title, content);
        }
        if (CHAT.equals(action)) {
            // 多轮对话：携带 Redis 历史上下文（按笔记隔离），成功后写回本轮问答
            List<ChatMessageVO> history =
                    userId == null ? List.of() : chatMemory.getHistory(userId, noteId);
            String answer = chatCompletion(props.getModel(), system, user, history);
            if (userId != null) {
                chatMemory.append(userId, noteId, question, answer);
            }
            return answer;
        }
        return chatCompletion(props.getModel(), system, user, List.of());
    }

    /**
     * 调 OpenAI 兼容 /chat/completions（messages = system + 历史 + 本轮输入）
     */
    private String chatCompletion(String model, String system, String user,
                                  List<ChatMessageVO> history) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("temperature", 0.6);
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", system));
        for (ChatMessageVO h : history) {
            messages.add(new JSONObject().set("role", h.getRole()).set("content", h.getContent()));
        }
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
            case CHAT -> "你是一位通用 AI 助手，像豆包一样与用户自然对话。请直接回答用户的问题：若问题与当前笔记内容相关，优先结合笔记内容作答；若与笔记无关，直接运用你自身的知识正常回答，不要声明与笔记无关，也不要拒绝回答。";
            default -> "你是一位智能助手，请直接、准确地回答用户。";
        };
    }

    private String userPrompt(String action, String title, String content, String question) {
        StringBuilder sb = new StringBuilder();
        // chat：通用对话，笔记仅作为可选参考上下文（可为空），问题置底
        if (CHAT.equals(action)) {
            if (StrUtil.isNotBlank(title)) {
                sb.append("当前笔记标题：").append(title).append("\n");
            }
            if (StrUtil.isNotBlank(content)) {
                sb.append("当前笔记内容（参考上下文，与问题无关时可忽略）：\n")
                        .append(content).append("\n\n");
            }
            sb.append("用户问题：").append(StrUtil.blankToDefault(question, "你好"));
            return sb.toString();
        }
        if (StrUtil.isNotBlank(title)) {
            sb.append("笔记标题：").append(title).append("\n");
        }
        if (StrUtil.isNotBlank(content)) {
            sb.append("笔记内容：\n").append(content).append("\n");
        }
        sb.append("\n请执行：").append(actionDesc(action)).append("。");
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
            case CHAT -> "（演示模式）我是 AI 助手。接入真实 AI 服务后，可以直接与我对话，像豆包一样向我提问。";
            default -> "未知操作";
        };
    }
}