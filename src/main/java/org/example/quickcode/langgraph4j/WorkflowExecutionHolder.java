package org.example.quickcode.langgraph4j;

import cn.hutool.json.JSONUtil;
import org.example.quickcode.ai.model.message.AiResponseMessage;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行期间的流式上下文，按 appId 存储，支持跨线程推送 SSE 数据。
 */
public final class WorkflowExecutionHolder {

    private static final ConcurrentHashMap<Long, Holder> ACTIVE_STREAMS = new ConcurrentHashMap<>();

    private WorkflowExecutionHolder() {
    }

    public static void set(Long appId, CodeGenTypeEnum codeGenType, FluxSink<String> sink) {
        if (appId == null || sink == null) {
            return;
        }
        ACTIVE_STREAMS.put(appId, new Holder(appId, codeGenType, sink));
    }

    public static void clear(Long appId) {
        if (appId != null) {
            ACTIVE_STREAMS.remove(appId);
        }
    }

    public static boolean isActive(Long appId) {
        return appId != null && ACTIVE_STREAMS.containsKey(appId);
    }

    /**
     * 推送工作流进度等纯文本（Vue 项目会自动包装为 JSON 消息）。
     */
    public static void emitChunk(Long appId, String text) {
        if (appId == null || text == null || text.isEmpty()) {
            return;
        }
        Holder holder = ACTIVE_STREAMS.get(appId);
        if (holder == null || holder.sink() == null) {
            return;
        }
        if (holder.codeGenType() == CodeGenTypeEnum.VUE_PROJECT) {
            emitRaw(appId, JSONUtil.toJsonStr(new AiResponseMessage(text)));
        } else {
            emitRaw(appId, text);
        }
    }

    /**
     * 直接推送已格式化的流式片段（不再二次包装）。
     */
    public static void emitRaw(Long appId, String chunk) {
        if (appId == null || chunk == null || chunk.isEmpty()) {
            return;
        }
        Holder holder = ACTIVE_STREAMS.get(appId);
        if (holder == null || holder.sink() == null) {
            return;
        }
        holder.sink().next(chunk);
    }

    private record Holder(Long appId, CodeGenTypeEnum codeGenType, FluxSink<String> sink) {
    }
}
