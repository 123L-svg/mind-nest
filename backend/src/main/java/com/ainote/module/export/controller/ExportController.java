package com.ainote.module.export.controller;

import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.mapper.NoteMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记导出接口（Markdown）
 */
@Tag(name = "导出模块")
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final NoteMapper noteMapper;

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern H = Pattern.compile("(?i)<h([1-6])[^>]*>(.*?)</h\\1>");
    private static final Pattern P = Pattern.compile("(?i)<p[^>]*>(.*?)</p>");
    private static final Pattern B = Pattern.compile("(?i)<(strong|b)[^>]*>(.*?)</\\1>");
    private static final Pattern I = Pattern.compile("(?i)<(em|i)[^>]*>(.*?)</\\1>");
    private static final Pattern A = Pattern.compile("(?i)<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>");
    private static final Pattern IMG = Pattern.compile("(?i)<img[^>]*src=\"([^\"]*)\"[^>]*>");
    private static final Pattern LI = Pattern.compile("(?i)<li[^>]*>(.*?)</li>");

    @Operation(summary = "导出笔记为 Markdown")
    @GetMapping("/markdown/{noteId}")
    public Result<String> markdown(@PathVariable("noteId") Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(SecurityUtil.getUserId())) {
            throw new BusinessException("笔记不存在或无权访问");
        }
        return Result.success(buildMarkdown(note));
    }

    private String buildMarkdown(Note note) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(note.getTitle()).append("\n\n");
        if (StrUtil.isNotBlank(note.getSummary())) {
            md.append("> ").append(note.getSummary()).append("\n\n");
        }
        String html = note.getContent() == null ? "" : note.getContent();
        html = html.replace("\n", "").replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n");
        md.append(htmlToMarkdown(html));
        return md.toString();
    }

    /** 简易 HTML -> Markdown，覆盖本系统富文本常用标签 */
    private String htmlToMarkdown(String html) {
        // 图片
        Matcher m = IMG.matcher(html);
        while (m.find()) {
            html = html.replace(m.group(0), "![image](" + m.group(1) + ")");
        }
        // 链接
        Matcher a = A.matcher(html);
        while (a.find()) {
            html = html.replace(a.group(0), "[" + stripTag(a.group(2)) + "](" + a.group(1) + ")");
        }
        // 标题
        Matcher h = H.matcher(html);
        while (h.find()) {
            String level = "#".repeat(Integer.parseInt(h.group(1)));
            html = html.replace(h.group(0), "\n\n" + level + " " + stripTag(h.group(2)) + "\n");
        }
        // 加粗
        Matcher b = B.matcher(html);
        while (b.find()) {
            html = html.replace(b.group(0), "**" + stripTag(b.group(2)) + "**");
        }
        // 斜体
        Matcher i = I.matcher(html);
        while (i.find()) {
            html = html.replace(i.group(0), "*" + stripTag(i.group(2)) + "*");
        }
        // 列表项
        Matcher li = LI.matcher(html);
        while (li.find()) {
            html = html.replace(li.group(0), "- " + stripTag(li.group(1)));
        }
        // 段落 -> 换行
        Matcher p = P.matcher(html);
        while (p.find()) {
            html = html.replace(p.group(0), stripTag(p.group(1)) + "\n\n");
        }
        return stripTag(html).trim();
    }

    private String stripTag(String s) {
        return TAG.matcher(s).replaceAll("").trim();
    }
}