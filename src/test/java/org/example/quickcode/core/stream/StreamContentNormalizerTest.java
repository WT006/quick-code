package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamContentNormalizerTest {

    @Test
    void normalize_closesOpenFence() {
        String input = "HTML代码生成中\n```html\n<!DOCTYPE html>\n<html></html>";
        String result = StreamContentNormalizer.normalize(input);
        assertTrue(result.endsWith("```"));
    }

    @Test
    void normalize_splitsStepFromFence() {
        String input = "HTML代码生成中```html\n<div></div>\n```";
        String result = StreamContentNormalizer.normalize(input);
        assertTrue(result.contains("HTML代码生成中\n\n```html"));
    }
}
