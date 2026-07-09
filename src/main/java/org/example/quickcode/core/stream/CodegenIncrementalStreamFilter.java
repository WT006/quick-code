package org.example.quickcode.core.stream;

import cn.hutool.core.util.StrUtil;

/**
 * 增量修改场景下的流式展示过滤：保留说明文字，屏蔽 Markdown 代码块内容。
 */
public final class CodegenIncrementalStreamFilter {

    public static final class State {
        private boolean inCodeBlock;
        private final StringBuilder pending = new StringBuilder();

        boolean isInCodeBlock() {
            return inCodeBlock;
        }
    }

    private CodegenIncrementalStreamFilter() {
    }

    public static State newState() {
        return new State();
    }

    public static String filterChunk(String chunk, boolean incremental, State state) {
        if (StrUtil.isBlank(chunk)) {
            return "";
        }
        if (!incremental) {
            return CodegenStepTextFilter.filterStreamChunk(chunk);
        }
        state.pending.append(chunk);
        StringBuilder display = new StringBuilder();
        drain(state, display);
        return CodegenStepTextFilter.filterStreamChunk(display.toString());
    }

    private static void drain(State state, StringBuilder display) {
        while (true) {
            if (!state.inCodeBlock) {
                int fenceStart = state.pending.indexOf("```");
                if (fenceStart < 0) {
                    int keep = partialBacktickSuffixLen(state.pending);
                    int emitLen = state.pending.length() - keep;
                    if (emitLen > 0) {
                        display.append(state.pending, 0, emitLen);
                        state.pending.delete(0, emitLen);
                    }
                    return;
                }
                display.append(state.pending, 0, fenceStart);
                state.pending.delete(0, fenceStart + 3);
                state.inCodeBlock = true;
                int newline = state.pending.indexOf("\n");
                if (newline >= 0) {
                    state.pending.delete(0, newline + 1);
                }
            } else {
                int fenceEnd = state.pending.indexOf("```");
                if (fenceEnd < 0) {
                    int keep = Math.min(2, state.pending.length());
                    if (state.pending.length() > keep) {
                        state.pending.delete(0, state.pending.length() - keep);
                    }
                    return;
                }
                state.pending.delete(0, fenceEnd + 3);
                if (!state.pending.isEmpty() && state.pending.charAt(0) == '\n') {
                    state.pending.deleteCharAt(0);
                }
                state.inCodeBlock = false;
            }
        }
    }

    private static int partialBacktickSuffixLen(StringBuilder sb) {
        int len = sb.length();
        if (len >= 2 && sb.charAt(len - 1) == '`' && sb.charAt(len - 2) == '`') {
            return 2;
        }
        if (len >= 1 && sb.charAt(len - 1) == '`') {
            return 1;
        }
        return 0;
    }
}
