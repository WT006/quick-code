package org.example.quickcode.core.stream;

import cn.hutool.core.util.StrUtil;

/**
 * 流式 AI 响应入库/解析前的内容规范化。
 */
public final class StreamContentNormalizer {

    private StreamContentNormalizer() {
    }

    public static String normalize(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String normalized = content
                .replace(
                        "\r\n",
                        "\n")
                .replaceAll(
                        "(HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成)\\s*(```)",
                        "$1\n\n$2")
                .replaceAll("```(html|css|javascript|js)\\s*(?=\\S)", "```$1\n");

        int fenceCount = countFences(normalized);
        if (fenceCount % 2 == 1) {
            normalized += "\n```";
        }
        return normalized;
    }

    private static int countFences(String content) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf("```", index)) >= 0) {
            count++;
            index += 3;
        }
        return count;
    }
}
