package com.syt.blog.util;

import com.syt.blog.common.AuthFailure;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 未认证（或认证已失效）时的响应。
 * <p>
 * 必须自定义：Security 过滤器链在 {@code DispatcherServlet} <b>之前</b>，
 * 所以这里抛出的异常<b>不会</b>被 {@code @RestControllerAdvice} 的 {@code GlobalExceptionHandler} 捕获。
 * 不自定义的话前端拿到的是 Spring 默认响应（空 body / 302 重定向），读不到
 * {@code Result{code, message}}，全局错误处理随之失效。
 * <p>
 * 失败原因优先取 {@code JwtAuthenticationFilter} 记下的 {@link AuthFailure}——
 * 这样「token 过期」返回 401001、「账号被禁用」返回 403002，而不是一律 401003。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 与 GlobalExceptionHandler 输出同构，前端沿用现有错误处理 */
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        AuthFailure failure = AuthFailure.of(request);
        if (failure == null) {
            failure = new AuthFailure(ErrorCode.TOKEN_MISSING, "Token 为空，请先登录");
        }

        // 账号被禁用等「已拿到身份但不允许继续」的情况是 403，其余一律 401
        int httpStatus = ErrorCode.isForbidden(failure.code())
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_UNAUTHORIZED;

        log.debug("鉴权未通过 status={} code={} msg={} uri={}",
                httpStatus, failure.code(), failure.message(), request.getRequestURI());

        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                jsonMapper.writeValueAsString(Result.error(failure.code(), failure.message())));
    }
}
