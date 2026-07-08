package org.example.quickcode.core.parser;

import org.example.quickcode.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult>{

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_HTML_PATTERN = Pattern.compile("(<!DOCTYPE[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(<html[\\s\\S]*?</html>)", Pattern.CASE_INSENSITIVE);

    /**
     * 解析多文件代码（HTML + CSS + JS）
     */
    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        // 设置HTML代码（优先 markdown 代码块，兜底尝试提取原始 HTML）
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            htmlCode = extractRawHtml(codeContent);
        }
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        // 设置CSS代码
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        // 设置JS代码
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 提取多文件代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 当模型未按 markdown 代码块输出时，尝试从原始文本中提取 HTML
     */
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
}

