package org.example.quickcode.core.stream;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.example.quickcode.ai.model.message.ToolExecutedMessage;
import org.example.quickcode.ai.model.message.ToolRequestMessage;
import org.example.quickcode.ai.tools.BaseTool;
import org.example.quickcode.ai.tools.ToolManager;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将 LangChain4j 工具调用消息格式化为前端可读的流式展示文本。
 */
@Component
public class StreamToolMessageFormatter {

    private static final Set<String> READ_TOOL_NAMES = Set.of("readFile", "readDir");

    private final ConcurrentHashMap<Long, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    @Resource
    private ToolManager toolManager;

    public void resetStepCounter(Long appId) {
        if (appId != null) {
            stepCounters.remove(appId);
        }
    }

    public String formatToolRequest(ToolRequestMessage toolRequestMessage, Set<String> seenToolIds) {
        String toolId = toolRequestMessage.getId();
        String toolName = toolRequestMessage.getName();
        if (READ_TOOL_NAMES.contains(toolName)) {
            return "";
        }
        if (toolId != null && seenToolIds.contains(toolId)) {
            return "";
        }
        if (toolId != null) {
            seenToolIds.add(toolId);
        }
        BaseTool tool = toolManager.getTool(toolName);
        String displayName = tool != null ? tool.getDisplayName() : toolName;
        return ("[选择工具] " + displayName).trim();
    }

    public String formatToolExecuted(ToolExecutedMessage toolExecutedMessage, Long appId) {
        String toolName = toolExecutedMessage.getName();
        JSONObject jsonObject = new JSONObject();
        if (StrUtil.isNotBlank(toolExecutedMessage.getArguments())) {
            jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
        }
        return formatExecutedLine(toolName, jsonObject, appId).trim();
    }

    public String buildHistorySummary(String toolName, JSONObject arguments, Long appId) {
        return formatExecutedLine(toolName, arguments, appId);
    }

    private String formatExecutedLine(String toolName, JSONObject arguments, Long appId) {
        BaseTool tool = toolManager.getTool(toolName);
        String displayName = tool != null ? tool.getDisplayName() : toolName;

        if (READ_TOOL_NAMES.contains(toolName)) {
            return "";
        }

        String relativePath = resolveRelativePath(toolName, arguments);
        if (StrUtil.isBlank(relativePath)) {
            return String.format("STEP %d：%s", nextStep(appId), displayName);
        }
        String fileName = FileUtil.getName(relativePath);
        return String.format("STEP %d：%s  %s  %s", nextStep(appId), displayName, fileName, relativePath);
    }

    private String formatReadLine(String toolName, String displayName, JSONObject arguments) {
        if ("readDir".equals(toolName)) {
            String relativeDirPath = arguments.getStr("relativeDirPath");
            if (StrUtil.isBlank(relativeDirPath)) {
                relativeDirPath = "项目根目录";
            }
            return String.format("正在读取目录：%s", relativeDirPath);
        }
        String relativeFilePath = arguments.getStr("relativeFilePath");
        if (StrUtil.isBlank(relativeFilePath)) {
            return String.format("正在%s", displayName);
        }
        return String.format("正在读取：%s", relativeFilePath);
    }

    private String resolveRelativePath(String toolName, JSONObject arguments) {
        if (arguments == null) {
            return null;
        }
        String relativeFilePath = arguments.getStr("relativeFilePath");
        if (StrUtil.isNotBlank(relativeFilePath)) {
            return relativeFilePath;
        }
        return null;
    }

    private int nextStep(Long appId) {
        if (appId == null) {
            return 1;
        }
        return stepCounters.computeIfAbsent(appId, ignored -> new AtomicInteger(0)).incrementAndGet();
    }
}
