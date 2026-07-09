package org.example.quickcode.core.parser;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 AI 响应中提取 Markdown 代码块，支持分阶段单文件解析。
 */
public final class CodeBlockExtractor {

    private static final Pattern HTML_FENCE = Pattern.compile("```html\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_FENCE = Pattern.compile("```css\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_FENCE = Pattern.compile("```(?:js|javascript)\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_FENCE = Pattern.compile("```([\\w-]*)\\s*\\n?([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private CodeBlockExtractor() {
    }

    public static String extractHtml(String content) {
        String fenced = extractFirst(HTML_FENCE, content);
        if (StrUtil.isNotBlank(fenced)) {
            return fenced.trim();
        }
        String only = extractSingleBlock(content);
        if (only != null && looksLikeHtml(only)) {
            return only.trim();
        }
        return extractRawHtml(content);
    }

    public static String extractCss(String content) {
        String fenced = extractFirst(CSS_FENCE, content);
        if (StrUtil.isNotBlank(fenced)) {
            return fenced.trim();
        }
        for (Block block : extractAllBlocks(content)) {
            if (isCssBlock(block.lang, block.code)) {
                return block.code.trim();
            }
        }
        String only = extractSingleBlock(content);
        if (only != null) {
            return only.trim();
        }
        return null;
    }

    public static String extractJs(String content) {
        String fenced = extractFirst(JS_FENCE, content);
        if (StrUtil.isNotBlank(fenced)) {
            return fenced.trim();
        }
        for (Block block : extractAllBlocks(content)) {
            if (isJsBlock(block.lang, block.code)) {
                return block.code.trim();
            }
        }
        String only = extractSingleBlock(content);
        if (only != null) {
            return only.trim();
        }
        if (looksLikeJs(content)) {
            return content.trim();
        }
        return null;
    }


    private static String extractFirst(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 分阶段场景：响应里通常只有 1 个代码块，直接取第一个非空块。
     */
    private static String extractSingleBlock(String content) {
        List<Block> blocks = extractAllBlocks(content);
        if (blocks.size() == 1 && StrUtil.isNotBlank(blocks.get(0).code)) {
            return blocks.get(0).code;
        }
        return null;
    }

    private static List<Block> extractAllBlocks(String content) {
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = ANY_FENCE.matcher(content);
        while (matcher.find()) {
            String lang = matcher.group(1) == null ? "" : matcher.group(1).toLowerCase();
            String code = matcher.group(2);
            if (StrUtil.isNotBlank(code)) {
                blocks.add(new Block(lang, code));
            }
        }
        return blocks;
    }

    private static String extractRawHtml(String content) {
        Pattern doctype = Pattern.compile("(<!DOCTYPE[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);
        Matcher m = doctype.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        Pattern htmlTag = Pattern.compile("(<html[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);
        m = htmlTag.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static boolean looksLikeHtml(String code) {
        String lower = code.trim().toLowerCase();
        return lower.contains("<!doctype") || lower.startsWith("<html");
    }

    private static boolean isCssBlock(String lang, String code) {
        return "css".equals(lang) || (looksLikeCss(code) && !looksLikeJs(code));
    }

    private static boolean isJsBlock(String lang, String code) {
        return "js".equals(lang) || "javascript".equals(lang) || looksLikeJs(code);
    }

    private static boolean looksLikeCss(String code) {
        return !code.contains("<") && code.contains("{") && code.contains("}");
    }

    private static boolean looksLikeJs(String code) {
        String trimmed = code.trim();
        return trimmed.contains("function")
                || trimmed.contains("const ")
                || trimmed.contains("let ")
                || trimmed.contains("var ")
                || trimmed.contains("document.")
                || trimmed.contains("addEventListener")
                || trimmed.contains("=>");
    }

    private record Block(String lang, String code) {
    }
}
