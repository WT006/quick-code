package org.example.quickcode.langgraph4j;

import org.example.quickcode.core.stream.StreamEventEncoder;
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

    /** 工作流阶段状态（前端单行展示，不混入正文） */
    public static void emitStatus(Long appId, String text) {
        emitRaw(appId, StreamEventEncoder.status(text));
    }

    /** AI 生成的正文内容（代码、方案等） */
    public static void emitContent(Long appId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        emitRaw(appId, StreamEventEncoder.content(text));
    }

    /** 工具调用信息（Vue 模式写文件等） */
    public static void emitTool(Long appId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        emitRaw(appId, StreamEventEncoder.tool(text));
    }

    /** 清空前端已展示的正文，避免重试时内容叠加导致卡顿 */
    public static void emitContentReset(Long appId) {
        emitRaw(appId, StreamEventEncoder.reset());
    }

    /** @deprecated 使用 {@link #emitStatus} */
    public static void emitChunk(Long appId, String text) {
        emitStatus(appId, text);
    }

    /**
     * 直接推送已编码的流式事件 JSON（不再二次包装）。
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
