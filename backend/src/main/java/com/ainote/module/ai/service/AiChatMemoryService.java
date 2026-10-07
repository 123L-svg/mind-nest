package com.ainote.module.ai.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ainote.module.ai.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话记忆（Redis）：
 * <ul>
 *   <li>以「用户 + 笔记」为单位存储多轮对话历史（List 结构，时间升序），
 *       不同笔记的对话相互隔离；新建未保存的笔记共用草稿桶（noteId=0）；</li>
 *   <li>每次写入 LTRIM 保留最近 {@link #MAX_MESSAGES} 条，防上下文无限膨胀；</li>
 *   <li>30 分钟无读写自动过期（滑动窗口，读写均续期）。</li>
 * </ul>
 * 仅 chat 动作读写记忆；outline/polish/summarize 不进入对话上下文。
 */
@Service
@RequiredArgsConstructor
public class AiChatMemoryService {

    private static final String KEY_PREFIX = "ai:chat:memory:";

    /** 保留最近的消息条数（约 10 轮对话） */
    private static final int MAX_MESSAGES = 20;

    /** 过期时间（分钟），每次读写都会续期 */
    private static final Duration TTL = Duration.ofMinutes(30);

    /** 单条消息最大保留字符数（控制上下文 token 规模） */
    private static final int MAX_CONTENT_CHARS = 2000;

    private final StringRedisTemplate redis;

    /** 读取该用户在指定笔记下的对话历史（时间升序），并续期；noteId 为空时使用草稿桶 */
    public List<ChatMessageVO> getHistory(Long userId, Long noteId) {
        String k = key(userId, noteId);
        List<String> raw = redis.opsForList().range(k, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        redis.expire(k, TTL);
        List<ChatMessageVO> list = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                JSONObject obj = JSONUtil.parseObj(s);
                ChatMessageVO vo = new ChatMessageVO();
                vo.setRole(obj.getStr("role"));
                vo.setContent(obj.getStr("content"));
                list.add(vo);
            } catch (Exception ignored) {
                // 脏数据跳过，不影响整体
            }
        }
        return list;
    }

    /** 追加一轮对话（用户提问 + AI 回答），裁剪保留最近 MAX_MESSAGES 条并续期 */
    public void append(Long userId, Long noteId, String userText, String aiText) {
        String k = key(userId, noteId);
        redis.opsForList().rightPushAll(k,
                toJson("user", userText), toJson("assistant", aiText));
        redis.opsForList().trim(k, -MAX_MESSAGES, -1);
        redis.expire(k, TTL);
    }

    /** 清空对话记忆 */
    public void clear(Long userId, Long noteId) {
        redis.delete(key(userId, noteId));
    }

    private String toJson(String role, String content) {
        String c = content == null ? "" : content;
        if (c.length() > MAX_CONTENT_CHARS) {
            c = c.substring(0, MAX_CONTENT_CHARS);
        }
        return new JSONObject().set("role", role).set("content", c).toString();
    }

    private String key(Long userId, Long noteId) {
        return KEY_PREFIX + userId + ":" + (noteId == null ? 0 : noteId);
    }
}
