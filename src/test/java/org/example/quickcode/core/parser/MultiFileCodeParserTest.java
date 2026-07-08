package org.example.quickcode.core.parser;

import org.example.quickcode.ai.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFileCodeParserTest {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();

    @Test
    void parseCode_withStepPrefixes_extractsAllFiles() {
        String content = """
                HTML代码生成中
                ```html
                <!DOCTYPE html>
                <html><head><link rel="stylesheet" href="style.css"></head><body><h1>Hi</h1></body></html>
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

        MultiFileCodeResult result = parser.parseCode(content);

        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertNotNull(result.getCssCode());
        assertTrue(result.getCssCode().contains("margin: 0"));
        assertNotNull(result.getJsCode());
        assertTrue(result.getJsCode().contains("console.log"));
    }

    @Test
    void parseCode_stepsOnly_returnsEmptyHtml() {
        MultiFileCodeResult result = parser.parseCode("HTML代码生成中\nCSS代码生成中\n代码文件已生成");
        assertTrue(result.getHtmlCode() == null || result.getHtmlCode().isBlank());
    }
}
