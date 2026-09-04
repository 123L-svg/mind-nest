package com.ainote.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HtmlSanitizer 富文本净化测试
 */
class HtmlSanitizerTest {

    @Test
    void stripsScriptTag() {
        String out = HtmlSanitizer.sanitize("<h2>标题</h2><script>alert(1)</script>");
        assertFalse(out.contains("script"), "script 标签应被剔除");
        assertTrue(out.contains("标题"), "正常内容应保留");
    }

    @Test
    void stripsInlineEventHandlers() {
        String out = HtmlSanitizer.sanitize("<p onclick=\"steal()\">正文</p>");
        assertFalse(out.contains("onclick"), "onclick 属性应被剔除");
        assertTrue(out.contains("正文"), "正文应保留");
    }

    @Test
    void stripsJavascriptHref() {
        String out = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">链接</a>");
        assertFalse(out.contains("javascript:"), "javascript: 协议链接应被剔除");
    }

    @Test
    void keepsRelativeImageSrc() {
        String out = HtmlSanitizer.sanitize("<img src=\"/api/files/20260827/a.png\">");
        assertTrue(out.contains("/api/files/20260827/a.png"), "相对图片路径应保留用于回显");
    }

    @Test
    void nullOrBlankReturnsAsIs() {
        assertTrue(HtmlSanitizer.sanitize(null) == null);
        assertTrue(HtmlSanitizer.sanitize("").isEmpty());
    }
}