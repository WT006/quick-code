package org.example.quickcode.core.stream;

import org.example.quickcode.ai.model.message.ToolExecutedMessage;
import org.example.quickcode.ai.model.message.ToolRequestMessage;
import org.example.quickcode.ai.tools.FileReadTool;
import org.example.quickcode.ai.tools.FileWriteTool;
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
        Map<String, org.example.quickcode.ai.tools.BaseTool> toolMap = new HashMap<>();
        toolMap.put("writeFile", new FileWriteTool());
        toolMap.put("readFile", new FileReadTool());
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
    void formatToolExecuted_readFile_returnsReadLineWithoutStep() {
        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("readFile");
        executed.setArguments("{\"relativeFilePath\":\"src/App.vue\"}");

        String display = formatter.formatToolExecuted(executed, 1L);

        assertEquals("", display);
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
        builder.appendRawContent("将创建商城首页和商品列表页。");

        ToolRequestMessage request = new ToolRequestMessage();
        request.setId("req-1");
        request.setName("writeFile");
        Set<String> seen = new HashSet<>();
        builder.appendTool(formatter.formatToolRequest(request, seen));

        ToolExecutedMessage executed = new ToolExecutedMessage();
        executed.setName("writeFile");
        executed.setArguments("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}");
        builder.appendTool(formatter.formatToolExecuted(executed, 1L));

        String message = builder.build();

        assertTrue(message.contains("[状态] 正在生成 Vue 项目代码"));
        assertTrue(message.contains("[选择工具] 写入文件"));
        assertTrue(message.contains("STEP 1：写入文件  App.vue  src/App.vue"));
        assertTrue(message.contains("将创建商城首页和商品列表页。"));
        assertTrue(!message.contains("<template>"));
        assertTrue(!message.contains("```"));
    }
}
