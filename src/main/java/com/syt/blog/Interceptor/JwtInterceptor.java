package com.syt.blog.Interceptor;

import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.util.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final JsonMapper jsonMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getRequestURL().toString();
        log.info("拦截 URL: {}", url);
        //忽略大小写比较
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        //获取请求头中的字段
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            log.warn("Token 为空");
            writeErrorResponse(response, ErrorCode.TOKEN_MISSING, "Token 为空，请先登录");
            return false;
        }

        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            Integer id = jwtUtils.getUserIdFromToken(token);
            log.info("User ID: {}", id);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            writeErrorResponse(response, ErrorCode.TOKEN_EXPIRED, "Token 已过期，请刷新");
            return false;
        } catch (Exception e) {
            log.warn("Token 无效: {}", e.getMessage());
            writeErrorResponse(response, ErrorCode.TOKEN_INVALID, "Token 无效，请重新登录");
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(code, message);
        response.getWriter().write(jsonMapper.writeValueAsString(result));
    }
}