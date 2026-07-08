package org.example.quickcode.core.stream;

import cn.hutool.core.util.StrUtil;

/**
 * 按事件到达顺序聚合流式内容，供入库与历史回放（开头 → 步骤 → 结尾）。
 */
public class StreamHistoryBuilder {

    public static final String STATUS_PREFIX = "[状态] ";

    private final StringBuilder timeline = new StringBuilder();

    public void appendStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return;
        }
        ensureLeadingNewline();
        timeline.append(STATUS_PREFIX).append(status.trim()).append('\n');
    }

    public void appendTool(String toolLine) {
        if (StrUtil.isBlank(toolLine)) {
            return;
        }
        ensureLeadingNewline();
        timeline.append(toolLine.trim()).append('\n');
    }

    public void appendRawContent(String content) {
        if (StrUtil.isNotBlank(content)) {
            timeline.append(content);
        }
    }

    public void resetContent() {
        timeline.setLength(0);
    }

    private void ensureLeadingNewline() {
        if (timeline.length() > 0 && timeline.charAt(timeline.length() - 1) != '\n') {
            timeline.append('\n');
        }
    }

    public String build() {
        String filtered = CodegenStepTextFilter.stripStepLines(
                StreamContentFilter.stripCodeBlocks(timeline.toString()));
        String message = filtered.trim();
        return StrUtil.isBlank(message) ? "代码生成已完成，请在右侧预览网站。" : message;
    }
}
