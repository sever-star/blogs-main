package com.syt.blog.util;

import com.syt.blog.common.AuthFailure;
import com.syt.blog.common.AuthUser;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：把 {@code Authorization: Bearer <access token>} 变成 SecurityContext 里的认证信息。
 * <p>
 * <b>关键约定：本过滤器从不自己写响应。</b>
 * <p>
 * Security 的过滤器链里有 {@code permitAll} 的路径（游客可读文章、游客可问 AI），这些路径的访问者
 * 本来就没有 token——过滤器若在此处直接 401，会把它们全部误伤。所以任何失败都只做两件事：
 * 不写入认证 + 把失败原因记到 request attribute（{@link AuthFailure}），然后继续放行。
 * 是否真的报错由后续的授权阶段决定：需要认证的路径才会走到
 * {@code JwtAuthenticationEntryPoint}，那时才把记录的原因回吐给前端。
 * <p>
 * 这样前端仍能区分「token 过期该刷新」(401001) 与「压根没登录」(401003)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** {@code blog_users.status}：1=正常，0=禁用（见 docs/enum-dictionary.md 2.3） */
    private static final short STATUS_ACTIVE = 1;

    /** {@code blog_users.role} 的兜底值；DB 上有 NOT NULL DEFAULT，这里只防御脏数据 */
    private static final String DEFAULT_ROLE = "USER";

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS 预检不带 Authorization，且必须原样放行，否则被当成未认证 → 浏览器报 CORS 错误
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 取 token
        String token = extractBearerToken(request);
        if (token == null) {
            // 没有 token：按匿名继续。permitAll 路径正常执行，需认证路径由 EntryPoint 兜底
            filterChain.doFilter(request, response);
            return;
        }
        // 解析 token
        Integer userId;
        try {
            userId = jwtUtils.getUserIdFromAccessToken(token);
        } catch (ExpiredJwtException e) {
            // 记录原因后继续放行：过期的 token 不该让游客路径报错
            fail(request, ErrorCode.TOKEN_EXPIRED, "Token 已过期，请刷新");
            filterChain.doFilter(request, response);
            return;
        } catch (Exception e) {
            // 含「类型不匹配」：拿 refresh token 当 Bearer 用会走到这里
            log.debug("JWT 解析失败: {}", e.getMessage());
            fail(request, ErrorCode.TOKEN_INVALID, "Token 无效，请重新登录");
            filterChain.doFilter(request, response);
            return;
        }

        // 每请求查一次库：降权立即生效（不用等 15 分钟 token 过期），并顺带校验账号状态
        var user = userRepository.findById(userId);
        if (user == null) {
            fail(request, ErrorCode.TOKEN_INVALID, "Token 无效，请重新登录");
            filterChain.doFilter(request, response);
            return;
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ACTIVE) {
            // 账号被禁用：不写入认证，交给 EntryPoint 返回 403 ACCOUNT_DISABLED
            fail(request, ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
            filterChain.doFilter(request, response);
            return;
        }
        /** 构建认证用户对象 **/
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), resolveRole(user.getRole()));
        //创建认证对象
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority(authUser.role())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        //把认证对象加入线程
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * 取出 Bearer 后的 JWT；头缺失、空、或不带 Bearer 前缀且整个值为空白时返回 null。
     * <p>
     * 与旧的 {@code JwtInterceptor} 一样兼容「不带 Bearer 前缀」的情况——直接把整个头当 token 解析，
     * 解析失败会走 {@code TOKEN_INVALID}，不会误判成 {@code TOKEN_MISSING}。
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
        return token.isBlank() ? null : token;
    }

    /** role 兜底：DB 有 NOT NULL DEFAULT 'USER'，此处只防御历史脏数据 */
    // role 为空或空白串时兜底为 USER
    private String resolveRole(String role) {
        return (role == null || role.isBlank()) ? DEFAULT_ROLE : role;
    }

    /** 记录本次请求的认证失败原因，不写响应 */
    private void fail(HttpServletRequest request, int code, String message) {
        request.setAttribute(AuthFailure.REQUEST_ATTRIBUTE, new AuthFailure(code, message));
    }
}
