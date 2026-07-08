package org.example.quickcode.core.stream;

import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 代码生成步骤文案过滤：步骤由系统 status 展示，正文只保留代码块。
 */
public final class CodegenStepTextFilter {

    private static final Pattern STEP_LINE_PATTERN = Pattern.compile(
            "^(HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成)\\s*$",
            Pattern.MULTILINE);

    private static final Pattern STEP_TEXT_INLINE_PATTERN = Pattern.compile(
            "HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成");

    private CodegenStepTextFilter() {
    }

    public static String stripStepLines(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String stripped = STEP_LINE_PATTERN.matcher(text).replaceAll("");
        return stripped.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * 流式 chunk 过滤：跳过纯步骤行，避免与 status 重复展示。
     */
    public static String filterStreamChunk(String chunk) {
        if (StrUtil.isBlank(chunk)) {
            return "";
        }
        if (STEP_LINE_PATTERN.matcher(chunk.trim()).matches()) {
            return "";
        }
        String filtered = chunk.replaceAll(
                "(?m)^(HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成)\\s*\\n?",
                "");
        return STEP_TEXT_INLINE_PATTERN.matcher(filtered).replaceAll("");
    }

    /**
     * 入库前对步骤行去重（保留顺序）。
     */
    public static String dedupeStepLines(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isStepLine(trimmed)) {
                if (seen.add(trimmed)) {
                    result.append(trimmed).append('\n');
                }
            } else {
                result.append(line).append('\n');
            }
        }
        return result.toString().trim();
    }

    public static boolean isStepLine(String line) {
        return line != null && STEP_LINE_PATTERN.matcher(line.trim()).matches();
    }
}
