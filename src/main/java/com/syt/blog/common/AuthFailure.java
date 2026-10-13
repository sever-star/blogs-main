package com.syt.blog.common;

/**
 * 认证失败的原因，由 {@code JwtAuthenticationFilter} 写进 request attribute，
 * 再由 {@code JwtAuthenticationEntryPoint} / {@code JwtAccessDeniedHandler} 取出写响应。
 * <p>
 * 为什么不直接在过滤器里写响应：Security 过滤器链里有 {@code permitAll} 的路径（游客可读文章、
 * 游客可问 AI），token 过期的用户访问这些路径时应当**当匿名继续**，而不是被拦成 401。
 * 所以过滤器只「记录原因」，由授权阶段决定要不要抛出——需要认证的路径才会走到 EntryPoint，
 * 那时才把记录的原因回吐给前端，前端也才能区分「过期该刷新」和「压根没登录」。
 *
 * @param code    业务码，取值见 {@link ErrorCode}
 * @param message 面向用户的提示文案
 */
public record AuthFailure(int code, String message) {

    /**
     * 请求属性 key，过滤器写、异常处理器读，两端必须一致。
     */
    public static final String REQUEST_ATTRIBUTE = AuthFailure.class.getName() + ".attribute";

    /**
     * 取出本请求的认证失败原因，没有则返回 null（即从未发起过认证）。
     */
    public static AuthFailure of(jakarta.servlet.ServletRequest request) {
        Object attr = request.getAttribute(REQUEST_ATTRIBUTE);
        return attr instanceof AuthFailure failure ? failure : null;
    }
}
