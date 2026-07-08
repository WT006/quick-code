package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamContentFilterTest {

    @Test
    void stripCodeBlocks_keepsStepText() {
        String input = """
                代码生成中
                ```html
                <html><body>hello</body></html>
                ```
                代码文件已生成
                """;

        String result = StreamContentFilter.stripCodeBlocks(input);

        assertTrue(result.contains("代码生成中"));
        assertTrue(result.contains("代码文件已生成"));
        assertTrue(!result.contains("<html>"));
        assertTrue(!result.contains("```"));
    }

    @Test
    void stripCodeBlocks_keepsMultiFileSteps() {
        String input = """
                HTML代码生成中
                ```html
                <div></div>
                ```
                CSS代码生成中
                ```css
                body { margin: 0; }
                ```
                JavaScript代码生成中
                ```javascript
                console.log('ok');
                ```
                代码文件已生成
                """;

        String result = StreamContentFilter.stripCodeBlocks(input);

        assertTrue(result.contains("HTML代码生成中"));
        assertTrue(result.contains("CSS代码生成中"));
        assertTrue(result.contains("JavaScript代码生成中"));
        assertTrue(result.contains("代码文件已生成"));
        assertTrue(!result.contains("```"));
        assertTrue(!result.contains("<div>"));
    }

    @Test
    void stripCodeBlocks_blankReturnsEmpty() {
        assertEquals("", StreamContentFilter.stripCodeBlocks(null));
        assertEquals("", StreamContentFilter.stripCodeBlocks("   "));
    }
}
