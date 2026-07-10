package org.example.quickcode.core;

/**
 * LangChain4j 流式 Agent 常见收尾异常识别。
 */
public final class LangChain4jStreamUtils {

    private LangChain4jStreamUtils() {
    }

    /**
     * 工具调用型 Agent 结束时，LangChain4j 可能因 null ChatResponse 抛出可忽略异常。
     */
    public static boolean isBenignNullResponseError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof IllegalArgumentException
                    && message != null
                    && message.contains("response cannot be null")) {
                return true;
            }
            if (current instanceof NullPointerException
                    && message != null
                    && message.contains("completeResponse")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * LangChain4j 单轮连续工具调用超过 maxSequentialToolsInvocations 时会抛出此异常。
     */
    public static boolean isToolLimitExceededError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("sequential tool invocations")
                    && message.contains("exceeded")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isRateLimitError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getName().contains("RateLimitException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("1302")
                    || message.contains("速率限制")
                    || message.contains("rate limit")
                    || message.contains("Rate Limit"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String resolveFriendlyErrorMessage(Throwable error) {
        if (isRateLimitError(error)) {
            return "智谱 API 调用频率超限（错误码 1302），请等待 1～2 分钟后重试";
        }
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : error.getMessage();
    }
}
