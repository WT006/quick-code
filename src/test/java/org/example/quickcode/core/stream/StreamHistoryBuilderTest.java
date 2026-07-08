package org.example.quickcode.core.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamHistoryBuilderTest {

    @Test
    void build_htmlWorkflowHistory() {
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        builder.appendStatus("正在收集图片");
        builder.appendStatus("搜集图片已完成");
        builder.appendRawContent("代码生成中\n```html\n<html></html>\n```\n代码文件已生成");
        builder.appendStatus("正在进行代码审查");
        builder.appendStatus("代码审查结束");

        String message = builder.build();

        assertTrue(message.contains("[状态] 正在收集图片"));
        assertTrue(message.contains("[状态] 搜集图片已完成"));
        assertTrue(!message.contains("代码生成中"));
        assertTrue(!message.contains("代码文件已生成"));
        assertTrue(message.contains("[状态] 正在进行代码审查"));
        assertTrue(message.contains("[状态] 代码审查结束"));
        assertTrue(!message.contains("```"));
        assertTrue(!message.contains("<html>"));
    }

    @Test
    void build_vueToolHistory() {
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        builder.appendStatus("正在生成 Vue 项目代码");
        builder.appendRawContent("将创建一个简单的商城首页和商品列表页。");
        builder.appendTool("[选择工具] 写入文件");
        builder.appendTool("STEP 1：写入文件  App.vue  src/App.vue");
        builder.appendTool("[选择工具] 写入文件");
        builder.appendTool("STEP 2：写入文件  main.js  src/main.js");

        String message = builder.build();

        assertTrue(message.contains("[状态] 正在生成 Vue 项目代码"));
        assertTrue(message.contains("[选择工具] 写入文件"));
        assertTrue(message.contains("STEP 1：写入文件  App.vue  src/App.vue"));
        assertTrue(message.contains("STEP 2：写入文件  main.js  src/main.js"));
        assertTrue(message.contains("将创建一个简单的商城首页和商品列表页。"));
    }

    @Test
    void resetContent_clearsTimeline() {
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        builder.appendStatus("代码审查未通过，准备重新生成");
        builder.appendRawContent("旧计划");
        builder.resetContent();
        builder.appendRawContent("新计划");

        String message = builder.build();

        assertTrue(!message.contains("旧计划"));
        assertTrue(message.contains("新计划"));
        assertTrue(!message.contains("[状态]"));
    }

    @Test
    void build_vueTimelineOrder() {
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        builder.appendStatus("正在收集图片");
        builder.appendStatus("正在生成 Vue 项目代码");
        builder.appendRawContent("将创建一个简单的商城首页。");
        builder.appendTool("[选择工具] 写入文件");
        builder.appendTool("STEP 1：写入文件  App.vue  src/App.vue");
        builder.appendStatus("正在进行代码审查");
        builder.appendStatus("代码审查结束");
        builder.appendStatus("✅ 代码生成已完成，请查看右侧预览");

        String message = builder.build();

        int earlyStatusIdx = message.indexOf("[状态] 正在生成 Vue 项目代码");
        int planIdx = message.indexOf("将创建一个简单的商城首页");
        int stepIdx = message.indexOf("STEP 1");
        int reviewIdx = message.indexOf("[状态] 正在进行代码审查");
        int endingIdx = message.indexOf("✅ 代码生成已完成");
        assertTrue(earlyStatusIdx >= 0 && planIdx > earlyStatusIdx && stepIdx > planIdx
                && reviewIdx > stepIdx && endingIdx > reviewIdx);
    }

    @Test
    void build_emptyReturnsDefaultMessage() {
        StreamHistoryBuilder builder = new StreamHistoryBuilder();
        assertEquals("代码生成已完成，请在右侧预览网站。", builder.build());
    }
}
