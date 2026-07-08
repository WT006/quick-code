package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 SSE 事件编码格式，供前端解析 t/d 字段。
 */
class StreamEventEncoderTest {

    @Test
    void statusEvent_hasExpectedShape() {
        String event = StreamEventEncoder.status("正在收集图片");

        assertTrue(StreamEventEncoder.isTypedEvent(event));
        assertEquals(StreamEventEncoder.TYPE_STATUS, StreamEventEncoder.getType(event));
        assertEquals("正在收集图片", StreamEventEncoder.getData(event));
    }

    @Test
    void toolEvent_hasExpectedShape() {
        String event = StreamEventEncoder.tool("STEP 1：写入文件  App.vue  src/App.vue");

        assertEquals(StreamEventEncoder.TYPE_TOOL, StreamEventEncoder.getType(event));
        assertEquals("STEP 1：写入文件  App.vue  src/App.vue", StreamEventEncoder.getData(event));
    }

    @Test
    void contentEvent_hasExpectedShape() {
        String event = StreamEventEncoder.content("代码生成中");

        assertEquals(StreamEventEncoder.TYPE_CONTENT, StreamEventEncoder.getType(event));
        assertEquals("代码生成中", StreamEventEncoder.getData(event));
    }

    @Test
    void resetEvent_hasExpectedShape() {
        String event = StreamEventEncoder.reset();

        assertTrue(StreamEventEncoder.isTypedEvent(event));
        assertEquals(StreamEventEncoder.TYPE_RESET, StreamEventEncoder.getType(event));
        assertEquals("", StreamEventEncoder.getData(event));
    }

    @Test
    void normalize_wrapsPlainTextAsContent() {
        String normalized = StreamEventEncoder.normalize("HTML代码生成中");

        assertTrue(StreamEventEncoder.isTypedEvent(normalized));
        assertEquals(StreamEventEncoder.TYPE_CONTENT, StreamEventEncoder.getType(normalized));
        assertEquals("HTML代码生成中", StreamEventEncoder.getData(normalized));
    }
}
