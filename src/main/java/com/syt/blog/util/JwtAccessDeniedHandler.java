package com.syt.blog.util;

import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 已认证但权限不足（如 USER 调写接口）时的响应 → HTTP 403。
 * <p>
 * 同 {@link JwtAuthenticationEntryPoint}：发生在 {@code DispatcherServlet} 之前，
 * 不会被 {@code GlobalExceptionHandler} 捕获，必须自己写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.debug("权限不足 uri={} reason={}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(
                Result.error(ErrorCode.ACCESS_DENIED, "权限不足，无法执行该操作")));
    }
}
