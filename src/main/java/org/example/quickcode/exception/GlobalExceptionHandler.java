package org.example.quickcode.exception;

import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.common.BaseResponse;
import org.example.quickcode.common.ResultUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理 SSE 请求的错误响应。
     * 若响应已由 Reactor 流式写出（isCommitted=true），则不再写入，避免冲突。
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (!isSseRequest(request)) {
            return false;
        }
        if (response == null) {
            return true;
        }
        if (response.isCommitted()) {
            log.warn("SSE 响应已提交，跳过 GlobalExceptionHandler 写入: {}", errorMessage);
            return true;
        }
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
            );
            String errorJson = JSONUtil.toJsonStr(errorData);
            String sseData = "event: business-error\ndata: " + errorJson + "\n\n";
            response.getWriter().write(sseData);
            response.getWriter().flush();
            response.getWriter().write("event: done\ndata: {}\n\n");
            response.getWriter().flush();
            return true;
        } catch (IllegalStateException | IOException ioException) {
            log.warn("SSE 错误响应写入失败（响应可能已被占用）: {}", ioException.getMessage());
            return true;
        }
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return (accept != null && accept.contains("text/event-stream"))
                || uri.contains("/chat/gen/code");
    }
}
