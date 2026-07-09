package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodegenIncrementalStreamFilterTest {

    @Test
    void nonIncremental_delegatesToStepFilter() {
        CodegenIncrementalStreamFilter.State state = CodegenIncrementalStreamFilter.newState();
        assertEquals("", CodegenIncrementalStreamFilter.filterChunk("CSS代码生成中\n", false, state));
    }

    @Test
    void incremental_keepsTextOutsideCodeBlock() {
        CodegenIncrementalStreamFilter.State state = CodegenIncrementalStreamFilter.newState();
        String part1 = CodegenIncrementalStreamFilter.filterChunk("好的，我来修改标题颜色。\n", true, state);
        String part2 = CodegenIncrementalStreamFilter.filterChunk("```html\n", true, state);
        String part3 = CodegenIncrementalStreamFilter.filterChunk("<html></html>\n```\n", true, state);

        assertTrue(part1.contains("修改标题"));
        assertEquals("", part2);
        assertEquals("", part3);
    }

    @Test
    void incremental_handlesSplitFenceMarkers() {
        CodegenIncrementalStreamFilter.State state = CodegenIncrementalStreamFilter.newState();
        CodegenIncrementalStreamFilter.filterChunk("说明文字`", true, state);
        String out = CodegenIncrementalStreamFilter.filterChunk("``html\n<div></div>\n```", true, state);

        assertEquals("", out);
        assertFalse(state.isInCodeBlock());
    }

    @Test
    void incremental_multipartStream() {
        CodegenIncrementalStreamFilter.State state = CodegenIncrementalStreamFilter.newState();
        assertEquals("已按你的要求调整。", CodegenIncrementalStreamFilter.filterChunk("已按你的要求调整。", true, state));

        String chunk1 = "```htm";
        String chunk2 = "l\n<body>test</body>\n```";
        assertEquals("", CodegenIncrementalStreamFilter.filterChunk(chunk1, true, state));
        assertEquals("", CodegenIncrementalStreamFilter.filterChunk(chunk2, true, state));
        assertFalse(state.isInCodeBlock());
    }
}
