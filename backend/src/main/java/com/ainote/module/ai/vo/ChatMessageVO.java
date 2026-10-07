package com.ainote.module.ai.vo;

import lombok.Data;

/**
 * 对话消息（多轮记忆中的一条）：role 为 user / assistant
 */
@Data
public class ChatMessageVO {

    private String role;

    private String content;
}
