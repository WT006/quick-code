package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodegenStepTextFilterTest {

    @Test
    void filterStreamChunk_skipsPureStepLine() {
        assertEquals("", CodegenStepTextFilter.filterStreamChunk("CSS代码生成中\n"));
    }

    @Test
    void filterStreamChunk_stripsInlineRepeatedStepText() {
        assertEquals("", CodegenStepTextFilter.filterStreamChunk("代码生成中代码生成中"));
    }

    @Test
    void stripStepLines_removesDuplicates() {
        String input = """
                HTML代码生成中
                CSS代码生成中
                JavaScript代码生成中
                代码文件已生成
                """;
        assertEquals("", CodegenStepTextFilter.stripStepLines(input).trim());
    }

    @Test
    void dedupeStepLines_keepsUniqueOrder() {
        String input = "HTML代码生成中\nHTML代码生成中\nCSS代码生成中\n";
        assertEquals("HTML代码生成中\nCSS代码生成中", CodegenStepTextFilter.dedupeStepLines(input));
    }
}
