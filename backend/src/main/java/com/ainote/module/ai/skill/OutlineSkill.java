package com.ainote.module.ai.skill;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 大纲技能：根据笔记标题和内容生成结构化大纲。
 * <p>参数（JSON params）：</p>
 * <ul>
 *   <li>type：organize（整理型，已有内容提炼）/ creative（创作型，空笔记构思），缺省时根据内容自动判断</li>
 *   <li>levels：层级深度 2 或 3，缺省 3</li>
 *   <li>withPoints：每个一级要点是否附一句要点说明，缺省 false</li>
 * </ul>
 */
@Component
public class OutlineSkill implements AiSkill {

    @Override
    public String name() {
        return "outline";
    }

    @Override
    public String description() {
        return "生成结构化大纲";
    }

    @Override
    public String systemPrompt(JSONObject params) {
        String type = params == null ? null : params.getStr("type");
        Integer levels = params == null ? null : params.getInt("levels");
        Boolean withPoints = params == null ? null : params.getBool("withPoints");

        int depth = (levels != null && levels > 0 && levels <= 4) ? levels : 3;
        boolean attachPoints = Boolean.TRUE.equals(withPoints);

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的内容架构师，擅长将信息组织为清晰的大纲结构。\n\n");

        if ("creative".equals(type)) {
            sb.append("任务：这是一篇尚未写作或仅有少量内容的笔记，");
            sb.append("请根据标题构思一份创作型大纲，帮助作者明确写作方向和结构。\n\n");
        } else if ("organize".equals(type)) {
            sb.append("任务：请仔细阅读笔记内容，提炼其中隐含的结构脉络，");
            sb.append("整理为一份忠实于原文的层级大纲。\n\n");
        } else {
            sb.append("任务：请根据提供的笔记标题和内容生成一份层级清晰的大纲。");
            sb.append("若笔记内容充实，提炼内容的内在结构；若笔记为空或仅有标题，");
            sb.append("则构思一份创作型大纲辅助后续写作。\n\n");
        }

        sb.append("输出要求：\n");
        sb.append("1. 使用 Markdown 无序列表，用 `-` 开头，子级用两个空格缩进\n");
        sb.append("2. 层级深度不超过 ").append(depth).append(" 级\n");
        sb.append("3. 每个要点必须是简洁的名词性短语，不超过 20 个字\n");
        sb.append("4. 要点之间逻辑独立、不重叠，合起来覆盖全部核心内容\n");
        if (attachPoints) {
            sb.append("5. 在每个一级要点后用一句话补充说明该部分应涵盖的内容\n");
        }
        sb.append("\n直接输出大纲本身，不要任何开场白、解释或总结。");
        return sb.toString();
    }

    @Override
    public String userPrompt(JSONObject params, String title, String content, String question) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append("笔记标题：").append(title.trim()).append("\n\n");
        }
        if (StrUtil.isNotBlank(content)) {
            sb.append("笔记内容：\n").append(content.trim()).append("\n\n");
        }
        sb.append("请生成大纲。");
        return sb.toString();
    }
}
