package org.example.quickcode.core.stream;

import cn.hutool.core.util.StrUtil;

/**
 * 流式内容过滤：入库时剔除代码块，保留步骤说明等纯文本。
 */
public final class StreamContentFilter {

    private StreamContentFilter() {
    }

    /**
     * 移除 Markdown 围栏代码块，保留步骤说明等正文。
     */
    public static String stripCodeBlocks(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String stripped = text.replaceAll("(?s)```[\\w-]*\\s*\\n.*?```", "");
        stripped = stripped.replaceAll("(?s)```.*?```", "");
        return stripped.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
