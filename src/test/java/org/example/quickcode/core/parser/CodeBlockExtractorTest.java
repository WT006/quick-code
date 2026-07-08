package org.example.quickcode.core.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeBlockExtractorTest {

    @Test
    void extractJs_singleBlockWithoutLangTag() {
        String content = """
                ``` 
                document.addEventListener('DOMContentLoaded', function() {
                  console.log('ready');
                });
                ```
                """;
        String js = CodeBlockExtractor.extractJs(content);
        assertNotNull(js);
        assertTrue(js.contains("addEventListener"));
    }

    @Test
    void extractJs_javascriptFence() {
        String content = """
                ```javascript
                const btn = document.querySelector('.btn');
                btn.addEventListener('click', () => alert('hi'));
                ```
                """;
        String js = CodeBlockExtractor.extractJs(content);
        assertNotNull(js);
        assertTrue(js.contains("querySelector"));
    }

    @Test
    void extractJs_onlyOneBlockNotAssignedToHtml() {
        String content = """
                ```js
                function init() { return 1; }
                ```
                """;
        String js = CodeBlockExtractor.extractJs(content);
        assertNotNull(js);
        assertTrue(js.contains("function init"));
    }

    @Test
    void extractCss_singleBlockWithBraces() {
        String content = """
                ```
                body { margin: 0; padding: 0; }
                .hero { display: flex; }
                ```
                """;
        String css = CodeBlockExtractor.extractCss(content);
        assertNotNull(css);
        assertTrue(css.contains("margin: 0"));
    }
}
