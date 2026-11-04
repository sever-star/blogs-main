package com.syt.blog.util;

import com.syt.blog.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权。
 * <p>
 * 浏览器 {@code new WebSocket()} 无法设置 Authorization 头，故 access token 通过
 * query 参数 {@code ?token=} 携带。此处复用与 {@link JwtAuthenticationFilter} 一致的
 * 校验链：解析 access token → 查库校验 tokenVersion（吊销立即生效）→ 校验账号状态，
 * 通过后把 userId 写入 session attributes 供后续 Handler 取用；任一步失败返回 false 拒绝握手。
 * <p>
 * 与 SecurityConfig 的配合：{@code /ws/**} 在 Security 链里 permitAll（握手鉴权不靠
 * Spring Security 的 Bearer 过滤器，而靠本拦截器），避免无 Authorization 头的 WS 握手
 * 被 {@code anyRequest().authenticated()} 拦成 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagesHandshakeInterceptor implements HandshakeInterceptor {

    /** {@code blog_users.status}：1=正常，0=禁用 */
    private static final short STATUS_ACTIVE = 1;

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null) {
            log.debug("WS 握手拒绝：缺少 token");
            return false;
        }
        Integer userId;
        try {
            userId = jwtUtils.getUserIdFromAccessToken(token);
        } catch (Exception e) {
            // 过期 / 签名无效 / 类型不匹配（refresh token 当 access 用）
            log.debug("WS 握手拒绝：token 无效或过期 - {}", e.getMessage());
            return false;
        }
        var user = userRepository.findById(userId);
        if (user == null) {
            return false;
        }
        long tv = jwtUtils.getTokenVersion(token);
        if (user.getTokenVersion() != null && tv != user.getTokenVersion()) {
            return false;
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ACTIVE) {
            return false;
        }
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /** 从 {@code ws://host/ws/messages?token=xxx} 的 query 里取 token */
    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && "token".equals(kv.substring(0, i))) {
                return kv.substring(i + 1);
            }
        }
        return null;
    }
}
