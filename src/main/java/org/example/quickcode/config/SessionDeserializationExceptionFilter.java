package org.example.quickcode.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Redis Session 反序列化失败时，清除无效 Session 并重试请求，避免直接 500。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionDeserializationExceptionFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE_NAME = "SESSION";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException ex) {
            if (!isSessionSerializationFailure(ex)) {
                throw ex;
            }
            log.warn("Redis session deserialization failed, clearing invalid session and retrying request");
            clearInvalidSession(request, response);
            filterChain.doFilter(createRequestWithoutSessionCookie(request), response);
        }
    }

    private void clearInvalidSession(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, null);
        cookie.setMaxAge(0);
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        response.addCookie(cookie);
    }

    private HttpServletRequest createRequestWithoutSessionCookie(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public Cookie[] getCookies() {
                Cookie[] cookies = super.getCookies();
                if (cookies == null) {
                    return null;
                }
                return Arrays.stream(cookies)
                        .filter(cookie -> !SESSION_COOKIE_NAME.equals(cookie.getName()))
                        .toArray(Cookie[]::new);
            }
        };
    }

    private boolean isSessionSerializationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SerializationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
