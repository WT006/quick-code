package org.example.quickcode.core.stream;

import org.example.quickcode.ai.model.message.ToolExecutedMessage;
import org.example.quickcode.ai.model.message.ToolRequestMessage;
import org.example.quickcode.ai.tools.*;
import org.example.quickcode.ai.tools.ToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamToolMessageFormatterTest {

    private StreamToolMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new StreamToolMessageFormatter();
        ToolManager toolManager = new ToolManager();
        Map<String, BaseTool> toolMap = new HashMap<>();
        toolMap.put("writeFile", new FileWriteTool());
        toolMap.put("readFile", new FileReadTool());
        toolMap.put("readDir", new FileDirReadTool());
        toolMap.put("modifyFile", new FileModifyTool());
        toolMap.put("deleteFile", new FileDeleteTool());
        ReflectionTestUtils.setField(toolManager, "toolMap", toolMap);
        ReflectionTestUtils.setField(formatter, "toolManager", toolManager);
    }

    @Test
    void formatToolRequest_returnsSelectToolLine() {
        ToolRequestMessage request = new ToolRequestMessage();
        request.setId("tool-1");
        request.setName("writeFile");

        String display = formatter.formatToolRequest(request, new HashSet<>());

        assertEquals("[选择工具] 写入文件", display);
    }

    @Test
    void formatToolRequest_readFile_returnsSelectToolLine() {
        ToolRequestMessage request = new ToolRequestMessage();
        request.setId("tool-read");
        request.setName("readFile");

        assertEquals("[选择工具] 读取文件", formatter.formatToolRequest(request, new HashSet<>()));
    }

    @Test
    void formatToolRequest_skipsDuplicateToolId() {
        ToolRequestMessage request = new ToolRequestMessage();
        request.setId("tool-1");
        request.setName("writeFile");
        Set<String> seen = new HashSet<>();

        assertEquals("[选择工具] 写入文件", formatter.formatToolRequest(request, seen));
        assertEquals("", formatter.formatToolRequest(request, seen));
    }

    @Test
    void formatToolExecuted_writeFile_returnsStepFormat() {
        formatter.resetStepCounter(1L);
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("writeFile");
        executed.setArguments("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}");

        String display = formatter.formatToolExecuted(executed, 1L);

        assertEquals("STEP 1：写入文件  App.vue  src/App.vue", display);
    }

    @Test
    void formatToolExecuted_incrementsStepPerAppId() {
        formatter.resetStepCounter(100L);

        ToolExecutedMessage first = new ToolExecutedMessage();
        first.setName("writeFile");
        first.setArguments("{\"relativeFilePath\":\"package.json\",\"content\":\"{}\"}");

        ToolExecutedMessage second = new ToolExecutedMessage();
        second.setName("writeFile");
        second.setArguments("{\"relativeFilePath\":\"src/main.js\",\"content\":\"// main\"}");

        assertEquals("STEP 1：写入文件  package.json  package.json", formatter.formatToolExecuted(first, 100L));
        assertEquals("STEP 2：写入文件  main.js  src/main.js", formatter.formatToolExecuted(second, 100L));
    }

    @Test
    void formatToolExecuted_readFile_returnsLightweightLine() {
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("readFile");
        executed.setArguments("{\"relativeFilePath\":\"src/App.vue\"}");

        assertEquals("正在读取：src/App.vue", formatter.formatToolExecuted(executed, 1L));
    }

    @Test
    void formatToolExecuted_readDir_returnsLightweightLine() {
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("readDir");
        executed.setArguments("{\"relativeDirPath\":\"src\"}");

        assertEquals("正在读取目录：src", formatter.formatToolExecuted(executed, 1L));
    }

    @Test
    void formatToolExecuted_modifyFile_returnsLightweightLine() {
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("modifyFile");
        executed.setArguments("{\"relativeFilePath\":\"src/App.vue\",\"oldContent\":\"a\",\"newContent\":\"b\"}");

        assertEquals("正在修改：src/App.vue", formatter.formatToolExecuted(executed, 1L));
    }

    @Test
    void formatToolExecuted_deleteFile_returnsLightweightLine() {
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("deleteFile");
        executed.setArguments("{\"relativeFilePath\":\"src/Old.vue\"}");

        assertEquals("正在删除：src/Old.vue", formatter.formatToolExecuted(executed, 1L));
    }

    @Test
    void readTools_doNotConsumeWriteStepCounter() {
        formatter.resetStepCounter(1L);

        ToolExecutedMessage read = new ToolExecutedMessage();
        read.setName("readFile");
        read.setArguments("{\"relativeFilePath\":\"src/App.vue\"}");

        ToolExecutedMessage write = new ToolExecutedMessage();
        write.setName("writeFile");
        write.setArguments("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}");

        assertEquals("正在读取：src/App.vue", formatter.formatToolExecuted(read, 1L));
        assertEquals("STEP 1：写入文件  App.vue  src/App.vue", formatter.formatToolExecuted(write, 1L));
    }

    @Test
    void resetStepCounter_restartsSequence() {
        formatter.resetStepCounter(9L);

        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("writeFile");
        executed.setArguments("{\"relativeFilePath\":\"index.html\",\"content\":\"<html></html>\"}");

        assertEquals("STEP 1：写入文件  index.html  index.html", formatter.formatToolExecuted(executed, 9L));
        assertEquals("STEP 2：写入文件  index.html  index.html", formatter.formatToolExecuted(executed, 9L));

        formatter.resetStepCounter(9L);
        assertEquals("STEP 1：写入文件  index.html  index.html", formatter.formatToolExecuted(executed, 9L));
    }

    @Test
    void vueWorkflowHistoryFormat_endToEnd() {
        formatter.resetStepCounter(1L);
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        builder.appendStatus("正在生成 Vue 项目代码");

        ToolRequestMessage readRequest = new ToolRequestMessage();
        readRequest.setId("req-read");
        readRequest.setName("readFile");
        Set<String> seen = new HashSet<>();
        builder.appendTool(formatter.formatToolRequest(readRequest, seen));

        ToolExecutedMessage readExecuted = new ToolExecutedMessage();
        readExecuted.setName("readFile");
        readExecuted.setArguments("{\"relativeFilePath\":\"src/App.vue\"}");
        builder.appendTool(formatter.formatToolExecuted(readExecuted, 1L));

        ToolRequestMessage writeRequest = new ToolRequestMessage();
        writeRequest.setId("req-1");
        writeRequest.setName("writeFile");
        builder.appendTool(formatter.formatToolRequest(writeRequest, seen));

        ToolExecutedMessage writeExecuted = new ToolExecutedMessage();
        writeExecuted.setName("writeFile");
        writeExecuted.setArguments("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}");
        builder.appendTool(formatter.formatToolExecuted(writeExecuted, 1L));

        String message = builder.build();

        assertTrue(message.contains("[状态] 正在生成 Vue 项目代码"));
        assertTrue(message.contains("[选择工具] 读取文件"));
        assertTrue(message.contains("正在读取：src/App.vue"));
        assertTrue(message.contains("[选择工具] 写入文件"));
        assertTrue(message.contains("STEP 1：写入文件  App.vue  src/App.vue"));
        assertTrue(!message.contains("<template>"));
        assertTrue(!message.contains("```"));
    }
}
