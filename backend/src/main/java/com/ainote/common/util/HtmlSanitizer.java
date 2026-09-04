package com.ainote.common.util;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 富文本 HTML 净化工具（jsoup 白名单）
 * <p>用于笔记内容入库前过滤，剥离 script/iframe/事件属性/javascript: 协议等，
 * 防止存储型 XSS。白名单在 relaxed 基础上补充表格与常用展示属性。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

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
        return Jsoup.clean(html, "", SAFELIST,
                new Document.OutputSettings().prettyPrint(false));
    }
}
