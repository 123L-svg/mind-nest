package com.ainote.common.util;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.Set;

/**
 * 富文本 HTML 净化工具（jsoup 白名单）
 * <p>用于笔记内容入库前过滤，剥离 script/iframe/事件属性/javascript: 协议等，
 * 防止存储型 XSS。白名单在 relaxed 基础上补充表格与常用展示属性。
 * <p>wangEditor 的颜色/字号/字体/行高/对齐等格式以内联 style 实现，
 * 因此对 style 做两级处理：先按 CSS 属性白名单过滤内容（阻断 url()/expression() 注入），
 * 再放行 style 属性本身。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    /** 允许保留的 CSS 属性（wangEditor 格式化所需的安全子集） */
    private static final Set<String> ALLOWED_CSS_PROPS = Set.of(
            "color", "background-color", "font-size", "font-family", "line-height",
            "text-align", "text-indent", "font-weight", "font-style", "text-decoration"
    );

    /** CSS 属性值允许的字符（字母数字、单位、颜色值、引号等；不含分号/反斜杠） */
    private static final String CSS_VALUE_PATTERN = "[a-zA-Z0-9_\\-.,%#()\\s'\"]*";

    private static final Safelist SAFELIST = Safelist.basic()
            // 常用块级/行内标签
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "span", "br", "hr",
                    "b", "strong", "i", "em", "u", "s", "del", "blockquote", "pre", "code",
                    "ul", "ol", "li", "img", "table", "thead", "tbody", "tr", "td", "th", "caption")
            // 图片：src 不做协议限制——一旦限制，相对路径（/api/files/...）会因不匹配
            // 协议被移除而破坏图片回显；on* 事件/script 已被白名单剥离，现代浏览器不会
            // 执行 img src 中的 javascript:。
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("a", "target")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("table", "border", "cellpadding", "cellspacing")
            .addAttributes("pre", "class")
            .addAttributes("code", "class")
            // style 在净化前已经过 CSS 属性白名单过滤（见 filterCss），可安全放行
            .addAttributes(":all", "style")
            // a 链接协议沿用 basic 自带的 http/https/mailto 限制
            .preserveRelativeLinks(true);

    /**
     * 净化富文本 HTML；空白输入原样返回。
     * 相对路径资源（如 /api/files/...）会被 jsoup 保留，不做协议校验。
     */
    public static String sanitize(String html) {
        if (StrUtil.isBlank(html)) {
            return html;
        }
        // 第一级：过滤 style 内容（仅保留白名单 CSS 属性）
        Document doc = Jsoup.parse(html);
        for (Element el : doc.getAllElements()) {
            String style = el.attr("style");
            if (!style.isBlank()) {
                el.attr("style", filterCss(style));
            }
        }
        // 第二级：jsoup 标签/属性白名单清洗
        return Jsoup.clean(doc.body().html(), "", SAFELIST,
                new Document.OutputSettings().prettyPrint(false));
    }

    /**
     * CSS 属性白名单过滤：
     * 仅保留允许的属性，且属性值必须通过字符白名单校验（阻断 url()/expression()/behavior 等注入向量）。
     */
    private static String filterCss(String style) {
        StringBuilder sb = new StringBuilder();
        for (String decl : style.split(";")) {
            int idx = decl.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String prop = decl.substring(0, idx).trim().toLowerCase();
            String val = decl.substring(idx + 1).trim();
            if (!ALLOWED_CSS_PROPS.contains(prop)) {
                continue;
            }
            String lowerVal = val.toLowerCase();
            if (!val.matches(CSS_VALUE_PATTERN)
                    || lowerVal.contains("url") || lowerVal.contains("expression")
                    || lowerVal.contains("javascript") || lowerVal.contains("behavior")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(prop).append(": ").append(val);
        }
        return sb.toString();
    }
}
