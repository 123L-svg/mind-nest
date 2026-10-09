package com.ainote.module.ai.skill;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 润色技能：根据风格与力度对笔记内容进行润色改写。
 * <p>参数（JSON params）：</p>
 * <ul>
 *   <li>style：润色风格（formal 正式 / casual 口语 / academic 学术 / vivid 生动 / concise 精炼），缺省 auto</li>
 *   <li>intensity：润色力度（light 轻改 / medium 中度 / heavy 重写），缺省 medium</li>
 *   <li>instruction：用户补充指令，如"统一为第三人称"</li>
 * </ul>
 */
@Component
public class PolishSkill implements AiSkill {

    @Override
    public String name() {
        return "polish";
    }

    @Override
    public String description() {
        return "按风格与力度润色内容";
    }

    @Override
    public String systemPrompt(JSONObject params) {
        String style = params == null ? null : params.getStr("style");
        String intensity = params == null ? null : params.getStr("intensity");

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深写作润色专家。\n\n");

        // 风格
        sb.append("润色风格：");
        switch (style == null ? "auto" : style) {
            case "formal" -> sb.append("正式严谨，适合职场与商务场合");
            case "casual" -> sb.append("轻松口语化，适合博客与日常分享");
            case "academic" -> sb.append("学术化表达，用词精准、逻辑严密");
            case "vivid" -> sb.append("生动活泼，可适当使用修辞增强画面感");
            case "concise" -> sb.append("精炼简洁，去除冗余表达");
            default -> sb.append("保持原文风格，仅在原文风格基础上微调");
        }
        sb.append("。\n");

        // 力度
        sb.append("润色力度：");
        switch (intensity == null ? "medium" : intensity) {
            case "light" -> sb.append("轻度润色，仅修正明显的语法、标点与错别字问题，尽量保留原句");
            case "heavy" -> sb.append("深度重写，可调整句式结构与段落顺序，使表达更流畅有力");
            default -> sb.append("中度润色，优化用词与句式，但不改变核心信息与段落结构");
        }
        sb.append("。\n\n");

        // 补充指令
        if (params != null && StrUtil.isNotBlank(params.getStr("instruction"))) {
            sb.append("补充要求：").append(params.getStr("instruction")).append("\n\n");
        }

        sb.append("通用原则：\n");
        // 选中润色：输入是笔记节选片段，避免 AI 补全结构
        if ("selection".equals(params == null ? null : params.getStr("scope"))) {
            sb.append("- 输入是笔记中节选的片段（可能不是完整段落），只润色该片段本身，不要补全结构或添加上下文\n");
        }
        sb.append("- 保留原文全部核心信息，不添加原文没有的新观点\n");
        sb.append("- 保留 Markdown 格式（标题、列表、粗斜体、代码块等）\n");
        sb.append("- 不添加任何解释、评述或前言后语\n\n");
        sb.append("直接输出润色后的完整内容，不要任何其他文字。");
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
        sb.append("请对以上内容进行润色。");
        return sb.toString();
    }
}
