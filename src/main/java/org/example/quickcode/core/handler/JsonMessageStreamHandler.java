package org.example.quickcode.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.ai.model.message.*;
import org.example.quickcode.core.stream.StreamEventEncoder;
import org.example.quickcode.core.stream.StreamHistoryBuilder;
import org.example.quickcode.core.stream.StreamToolMessageFormatter;
import org.example.quickcode.model.entity.User;
import org.example.quickcode.model.enums.ChatHistoryMessageTypeEnum;
import org.example.quickcode.service.ChatHistoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private StreamToolMessageFormatter streamToolMessageFormatter;

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StreamHistoryBuilder historyBuilder = new StreamHistoryBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> handleChunk(chunk, historyBuilder, seenToolIds, appId))
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> {
                    streamToolMessageFormatter.resetStepCounter(appId);
                    String aiResponse = historyBuilder.build();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    streamToolMessageFormatter.resetStepCounter(appId);
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    private String handleChunk(String chunk, StreamHistoryBuilder historyBuilder, Set<String> seenToolIds, long appId) {
        if (StreamEventEncoder.isTypedEvent(chunk)) {
            appendTypedToHistory(chunk, historyBuilder);
            return chunk;
        }
        if (!chunk.startsWith("{")) {
            return StreamEventEncoder.content(chunk);
        }
        return convertLangChainChunk(chunk, historyBuilder, seenToolIds, appId);
    }

    private void appendTypedToHistory(String chunk, StreamHistoryBuilder historyBuilder) {
        String type = StreamEventEncoder.getType(chunk);
        String data = StreamEventEncoder.getData(chunk);
        if (StreamEventEncoder.TYPE_STATUS.equals(type)) {
            historyBuilder.appendStatus(data);
        } else if (StreamEventEncoder.TYPE_TOOL.equals(type)) {
            historyBuilder.appendTool(data);
        } else if (StreamEventEncoder.TYPE_RESET.equals(type)) {
            historyBuilder.resetContent();
        }
    }

    private String convertLangChainChunk(String chunk, StreamHistoryBuilder historyBuilder, Set<String> seenToolIds, long appId) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            return "";
        }
        return switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                yield StreamEventEncoder.content(data);
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String display = streamToolMessageFormatter.formatToolRequest(toolRequestMessage, seenToolIds);
                if (StrUtil.isBlank(display)) {
                    yield "";
                }
                historyBuilder.appendTool(display);
                yield StreamEventEncoder.tool(display);
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String display = streamToolMessageFormatter.formatToolExecuted(toolExecutedMessage, appId);
                if (StrUtil.isBlank(display)) {
                    yield "";
                }
                historyBuilder.appendTool(display);
                yield StreamEventEncoder.tool(display);
            }
        };
    }
}
