package org.example.quickcode.core.parser;

import org.example.quickcode.ai.model.MultiFileCodeResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\s*\\n?([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_HTML_PATTERN = Pattern.compile("(<!DOCTYPE[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(<html[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);

    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);

        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            htmlCode = extractRawHtml(codeContent);
        }

        ParsedBlocks fallback = parseGenericCodeBlocks(codeContent);
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            htmlCode = fallback.htmlCode;
        }
        if (cssCode == null || cssCode.trim().isEmpty()) {
            cssCode = fallback.cssCode;
        }
        if (jsCode == null || jsCode.trim().isEmpty()) {
            jsCode = fallback.jsCode;
        }

        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    private static ParsedBlocks parseGenericCodeBlocks(String content) {
        ParsedBlocks blocks = new ParsedBlocks();
        List<String> languages = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        Matcher matcher = ANY_CODE_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            languages.add(matcher.group(1) == null ? "" : matcher.group(1).toLowerCase());
            codes.add(matcher.group(2));
        }
        for (int i = 0; i < codes.size(); i++) {
            String lang = languages.get(i);
            String code = codes.get(i);
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            if (blocks.htmlCode == null && isHtmlLanguage(lang, code)) {
                blocks.htmlCode = code;
            } else if (blocks.cssCode == null && isCssLanguage(lang, code)) {
                blocks.cssCode = code;
            } else if (blocks.jsCode == null && isJsLanguage(lang, code)) {
                blocks.jsCode = code;
            }
        }
        if (blocks.htmlCode == null && !codes.isEmpty()) {
            blocks.htmlCode = codes.get(0);
        }
        if (blocks.cssCode == null && codes.size() > 1) {
            blocks.cssCode = codes.get(1);
        }
        if (blocks.jsCode == null && codes.size() > 2) {
            blocks.jsCode = codes.get(2);
        }
        return blocks;
    }

    private static boolean isHtmlLanguage(String lang, String block) {
        return lang.contains("html") || block.trim().toLowerCase().contains("<!doctype")
                || block.trim().toLowerCase().startsWith("<html");
    }

    private static boolean isCssLanguage(String lang, String block) {
        return lang.equals("css") || (!block.contains("<") && block.contains("{") && block.contains("}"));
    }

    private static boolean isJsLanguage(String lang, String block) {
        return lang.equals("js") || lang.equals("javascript")
                || block.contains("function") || block.contains("const ") || block.contains("document.");
    }

    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractRawHtml(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher doctypeMatcher = RAW_HTML_PATTERN.matcher(content);
        if (doctypeMatcher.find()) {
            return doctypeMatcher.group(1);
        }
        Matcher htmlTagMatcher = HTML_TAG_PATTERN.matcher(content);
        if (htmlTagMatcher.find()) {
            return htmlTagMatcher.group(1);
        }
        return null;
    }

    private static final class ParsedBlocks {
        private String htmlCode;
        private String cssCode;
        private String jsCode;
    }
}
