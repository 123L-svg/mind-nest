package com.ainote.module.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.module.ai.config.AiProperties;
import com.ainote.module.ai.skill.AiSkill;
import com.ainote.module.ai.skill.AiSkillRegistry;
import com.ainote.module.ai.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI 能力服务（OpenAI 兼容 Chat Completions）
 * <p>技能化重构：每种 AI 能力（大纲/润色/摘要/问答）封装为独立的 {@link AiSkill} 实现，
 * 本服务只负责：1) 按名称从注册表分发；2) 携带对话历史（chat 技能）；3) 统一调用 LLM。</p>
 * <p>chat 为多轮对话：从 Redis 读取该用户的历史上下文一并提交给大模型，
 * 成功后把本轮问答写回 {@link AiChatMemoryService}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    /** 技能名常量（与各 Skill 实现的 name() 对应） */
    public static final String OUTLINE = "outline";
    public static final String POLISH = "polish";
    public static final String SUMMARIZE = "summarize";
    public static final String CHAT = "chat";

    private final AiProperties props;
    private final AiSkillRegistry skillRegistry;
    private final AiChatMemoryService chatMemory;

    /**
     * 执行 AI 操作，返回文本结果。
     *
     * @param userId   当前用户ID（chat 用于多轮记忆）
     * @param noteId   笔记ID（chat 多轮记忆按笔记隔离；可空则使用草稿桶）
     * @param action   技能名（outline/polish/summarize/chat）
     * @param params   技能参数（JSON 字符串，可为空）
     * @param title    笔记标题
     * @param content  笔记内容
     * @param question 用户问题（chat 技能使用）
     * @return AI 生成结果
     */
    public String execute(Long userId, Long noteId, String action,
                          String params, String title, String content, String question) {

        // 1. 按名称从注册表获取技能
        AiSkill skill = skillRegistry.require(action);
        JSONObject skillParams = parseParams(params);

        // 2. 构建提示词
        String system = skill.systemPrompt(skillParams);
        String user = skill.userPrompt(skillParams, title, content, question);

        // 3. Mock 模式：直接返回模拟结果，不读写对话记忆（避免污染真实上下文）
        if (props.isMockEnabled() || !props.isRealReady()) {
            return skill.postProcess(mock(action, title, content), skillParams);
        }

        // 4. 构建对话历史（chat 技能：携带多轮记忆）
        List<ChatMessageVO> history = List.of();
        if (CHAT.equals(action) && userId != null) {
            history = chatMemory.getHistory(userId, noteId);
        }

        // 5. 调用 LLM
        String rawResult = chatCompletion(props.getModel(), system, user, history);

        // 6. chat 技能：将本轮问答写入对话记忆
        if (CHAT.equals(action) && userId != null) {
            chatMemory.append(userId, noteId, question, rawResult);
        }

        // 7. 结果后处理（默认直接返回原文）
        return skill.postProcess(rawResult, skillParams);
    }

    private JSONObject parseParams(String params) {
        if (StrUtil.isBlank(params)) {
            return new JSONObject();
        }
        try {
            JSONObject ret = JSONUtil.parseObj(params);
            return ret == null ? new JSONObject() : ret;
        } catch (Exception e) {
            log.warn("技能参数解析失败，将忽略参数执行：{}", e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 流式对话（SSE）：逐 token 回调 onDelta，返回完整回答。
     * <p>与 {@link #execute} 的区别：不走 MQ 异步链路，由 SSE 端点直连调用；
     * 携带 Redis 历史上下文，但记忆写回由调用方在流结束后决定（mock 时跳过）。</p>
     */
    public String chatStream(Long userId, Long noteId, String title, String content,
                             String question, Consumer<String> onDelta) {
        AiSkill skill = skillRegistry.require(CHAT);
        JSONObject empty = new JSONObject();
        String system = skill.systemPrompt(empty);
        String user = skill.userPrompt(empty, title, content, question);

        // Mock 模式：模拟逐字输出，不读写对话记忆
        if (props.isMockEnabled() || !props.isRealReady()) {
            return mockStream(onDelta);
        }
        List<ChatMessageVO> history = userId == null ? List.of() : chatMemory.getHistory(userId, noteId);
        return chatCompletionStream(props.getModel(), system, user, history, onDelta);
    }

    /**
     * 流式调 OpenAI 兼容 /chat/completions（stream=true）。
     * <p>SSE 帧格式：data:{"choices":[{"delta":{"content":"..."}}]}，data:[DONE] 结束；
     * 客户端断开时 onDelta 内的 emitter.send 会抛异常，向上传播以中断读取。</p>
     */
    private String chatCompletionStream(String model, String system, String user,
                                        List<ChatMessageVO> history, Consumer<String> onDelta) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("temperature", 0.6);
        body.set("stream", true);
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", system));
        for (ChatMessageVO h : history) {
            messages.add(new JSONObject().set("role", h.getRole()).set("content", h.getContent()));
        }
        messages.add(new JSONObject().set("role", "user").set("content", user));
        body.set("messages", messages);

        String url = StrUtil.removeSuffix(props.getBaseUrl(), "/") + "/chat/completions";
        StringBuilder full = new StringBuilder();
        // executeAsync：立即返回不预读 body，bodyStream() 才是真实网络流（逐 token 到达）
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout((int) props.getTimeoutMillis())
                .body(body.toString())
                .executeAsync()) {
            if (!resp.isOk()) {
                throw new BusinessException("AI 调用失败：" + resp.body());
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.bodyStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String data = parseSseData(line);
                    if (data == null || "[DONE]".equals(data)) {
                        if ("[DONE]".equals(data)) break;
                        continue;
                    }
                    String delta = extractDeltaContent(data);
                    if (StrUtil.isNotEmpty(delta)) {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 流式调用异常：" + e.getMessage());
        }
        return full.toString();
    }

    /** 解析 SSE 行：data:xxx → xxx；注释(:开头)/空行/非 data 行返回 null */
    private String parseSseData(String line) {
        if (StrUtil.isBlank(line) || line.startsWith(":") || !line.startsWith("data:")) {
            return null;
        }
        return line.substring(5).trim();
    }

    /** 从流式 chunk JSON 提取增量文本（choices[0].delta.content），解析失败返回 null */
    private String extractDeltaContent(String data) {
        try {
            JSONArray choices = JSONUtil.parseObj(data).getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            return delta == null ? null : delta.getStr("content");
        } catch (Exception e) {
            return null;
        }
    }

    /** Mock 流式：模拟逐字输出（每 2 字一推，体验打字机效果） */
    private String mockStream(Consumer<String> onDelta) {
        String text = mock(CHAT, null, null);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i += 2) {
            String piece = text.substring(i, Math.min(i + 2, text.length()));
            sb.append(piece);
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            onDelta.accept(piece);
        }
        return sb.toString();
    }

    /**
     * 调 OpenAI 兼容 /chat/completions。
     */
    private String chatCompletion(String model, String system, String user,
                                  List<ChatMessageVO> history) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("temperature", 0.6);

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", system));
        for (ChatMessageVO h : history) {
            messages.add(new JSONObject().set("role", h.getRole())
                    .set("content", h.getContent()));
        }
        messages.add(new JSONObject().set("role", "user").set("content", user));
        body.set("messages", messages);

        String url = StrUtil.removeSuffix(props.getBaseUrl(), "/") + "/chat/completions";
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .timeout((int) props.getTimeoutMillis())
                .body(body.toString())
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

    /** 未配置 key 时的模拟结果（开发演示用） */
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
