package com.ainote.module.ai.skill;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 摘要技能：根据笔记内容生成摘要。
 * <p>参数（JSON params）：</p>
 * <ul>
 *   <li>length：摘要长度（short 一句话 / medium 一段话 / long 多段），缺省 medium</li>
 *   <li>keywords：需重点突出的关键词，逗号分隔，如"架构,性能,部署"</li>
 *   <li>audience：受众视角（beginner 入门 / expert 专家 / auto 自动），影响专业术语使用程度</li>
 * </ul>
 */
@Component
public class SummarizeSkill implements AiSkill {

    @Override
    public String name() {
        return "summarize";
    }

    @Override
    public String description() {
        return "生成内容摘要";
    }

    @Override
    public String systemPrompt(JSONObject params) {
        String length = params == null ? null : params.getStr("length");
        String keywords = params == null ? null : params.getStr("keywords");
        String audience = params == null ? null : params.getStr("audience");

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的内容摘要专家，擅长用最少的文字精准概括核心信息。\n\n");

        sb.append("摘要要求：");
        switch (length == null ? "medium" : length) {
            case "short" -> sb.append("用一句话概括，不超过 30 个字。\n");
            case "long" -> sb.append("用多段详细描述，包含背景、方法、结论与意义，总计 200~400 字。\n");
            default -> sb.append("用一段话概括，50~100 字，涵盖最核心的观点与结论。\n");
        }

        if (StrUtil.isNotBlank(keywords)) {
            sb.append("重点突出以下关键词：").append(keywords.trim()).append("。\n");
        }

        if (audience != null) {
            switch (audience) {
                case "beginner" -> sb.append("面向初学者，避免过多专业术语，必要时用通俗语言解释概念。\n");
                case "expert" -> sb.append("面向领域专家，可使用专业术语，突出技术细节与深度。\n");
                default -> sb.append("保持中性的语言风格，兼顾可读性与专业性。\n");
            }
        }

        sb.append("\n输出要求：\n");
        sb.append("- 使用中文（若笔记为英文则使用英文）\n");
        sb.append("- 使用第三人称客观陈述\n");
        sb.append("- 不使用 Markdown 格式，输出纯文本\n");
        sb.append("- 直接输出摘要内容，不要任何前言、后缀或解释\n");

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
        sb.append("请生成摘要。");
        return sb.toString();
    }
}
