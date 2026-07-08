package org.example.quickcode.core.handler;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.core.stream.StreamEventEncoder;
import org.example.quickcode.core.stream.StreamHistoryBuilder;
import org.example.quickcode.model.entity.User;
import org.example.quickcode.model.enums.ChatHistoryMessageTypeEnum;
import org.example.quickcode.service.ChatHistoryService;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StreamHistoryBuilder historyBuilder = new StreamHistoryBuilder();
        return originFlux
                .map(chunk -> mapChunk(chunk, historyBuilder))
                .doOnComplete(() -> {
                    String aiResponse = historyBuilder.build();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    private String mapChunk(String chunk, StreamHistoryBuilder historyBuilder) {
        if (StreamEventEncoder.isTypedEvent(chunk)) {
            String type = StreamEventEncoder.getType(chunk);
            String data = StreamEventEncoder.getData(chunk);
            if (StreamEventEncoder.TYPE_STATUS.equals(type)) {
                historyBuilder.appendStatus(data);
            } else if (StreamEventEncoder.TYPE_RESET.equals(type)) {
                historyBuilder.resetContent();
            } else if (StreamEventEncoder.TYPE_CONTENT.equals(type)) {
                historyBuilder.appendRawContent(data);
            }
            return chunk;
        }
        historyBuilder.appendRawContent(chunk);
        return StreamEventEncoder.content(chunk);
    }
}
