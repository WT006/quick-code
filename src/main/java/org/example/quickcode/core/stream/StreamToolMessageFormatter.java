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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将 LangChain4j 工具调用消息格式化为前端可读的流式展示文本。
 * 写入类工具使用 STEP 序号；读取/修改/删除等辅助工具使用轻量「正在xxx」格式。
 */
@Component
public class StreamToolMessageFormatter {

    private static final Set<String> WRITE_TOOL_NAMES = Set.of("writeFile");

    private static final Map<String, String> LIGHTWEIGHT_ACTION_PREFIX = Map.of(
            "readFile", "正在读取",
            "readDir", "正在读取目录",
            "modifyFile", "正在修改",
            "deleteFile", "正在删除"
    );

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
        if (WRITE_TOOL_NAMES.contains(toolName)) {
            return formatWriteStepLine(toolName, arguments, appId);
        }
        if (LIGHTWEIGHT_ACTION_PREFIX.containsKey(toolName)) {
            return formatLightweightLine(toolName, arguments);
        }
        BaseTool tool = toolManager.getTool(toolName);
        String displayName = tool != null ? tool.getDisplayName() : toolName;
        String relativePath = resolveRelativePath(arguments);
        if (StrUtil.isNotBlank(relativePath)) {
            return String.format("正在操作：%s  %s", displayName, relativePath);
        }
        return String.format("正在操作：%s", displayName);
    }

    private String formatWriteStepLine(String toolName, JSONObject arguments, Long appId) {
        BaseTool tool = toolManager.getTool(toolName);
        String displayName = tool != null ? tool.getDisplayName() : toolName;
        String relativePath = resolveRelativePath(arguments);
        if (StrUtil.isBlank(relativePath)) {
            return String.format("STEP %d：%s", nextStep(appId), displayName);
        }
        String fileName = FileUtil.getName(relativePath);
        return String.format("STEP %d：%s  %s  %s", nextStep(appId), displayName, fileName, relativePath);
    }

    private String formatLightweightLine(String toolName, JSONObject arguments) {
        String prefix = LIGHTWEIGHT_ACTION_PREFIX.get(toolName);
        if ("readDir".equals(toolName)) {
            String relativeDirPath = arguments.getStr("relativeDirPath");
            if (StrUtil.isBlank(relativeDirPath)) {
                relativeDirPath = "项目根目录";
            }
            return prefix + "：" + relativeDirPath;
        }
        String relativePath = resolveRelativePath(arguments);
        if (StrUtil.isBlank(relativePath)) {
            return prefix;
        }
        return prefix + "：" + relativePath;
    }

    private String resolveRelativePath(JSONObject arguments) {
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
