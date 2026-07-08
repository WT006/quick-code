package org.example.quickcode.core.stream;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * SSE 流式事件编码：区分状态、工具调用、正文内容，供前端分区展示。
 */
public final class StreamEventEncoder {

    public static final String TYPE_STATUS = "status";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_TOOL = "tool";
    public static final String TYPE_RESET = "reset";

    private StreamEventEncoder() {
    }

    public static String status(String data) {
        return encode(TYPE_STATUS, data);
    }

    public static String content(String data) {
        return encode(TYPE_CONTENT, data);
    }

    public static String tool(String data) {
        return encode(TYPE_TOOL, data);
    }

    /** 通知前端清空已累积的正文（质检重试等场景） */
    public static String reset() {
        return encode(TYPE_RESET, "");
    }

    public static String encode(String type, String data) {
        return JSONUtil.toJsonStr(Map.of("t", type, "d", data == null ? "" : data));
    }

    public static boolean isTypedEvent(String chunk) {
        if (chunk == null || !chunk.startsWith("{")) {
            return false;
        }
        try {
            JSONObject json = JSONUtil.parseObj(chunk);
            return json.containsKey("t") && json.containsKey("d");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String getType(String chunk) {
        return JSONUtil.parseObj(chunk).getStr("t");
    }

    public static String getData(String chunk) {
        return JSONUtil.parseObj(chunk).getStr("d");
    }

    public static String normalize(String chunk) {
        return isTypedEvent(chunk) ? chunk : content(chunk);
    }
}
