package com.syt.blog.common;

/**
 * 已认证用户的最小身份信息，作为 Spring Security 的认证主体（principal）。
 * <p>
 * 由 {@code JwtAuthenticationFilter} 从 {@code blog_users} 组装后塞进 SecurityContext，
 * Controller / Service 通过 {@code SecurityUtils.currentUserId()} 取用，
 * 不再手动解析 Authorization 头。
 * <p>
 * 只放当前请求真正需要的三个字段，避免把整行用户数据（含密码散列）带进认证上下文。
 *
 * @param id       用户主键
 * @param username 登录名
 * @param role     角色，取值见 {@code blog_users.role}：ADMIN=博主/管理员，USER=普通读者。
 *                 注意拼的是 Spring Security 的 authority 原文，**不带** {@code ROLE_} 前缀，
 *                 所以授权规则要用 {@code hasAuthority("ADMIN")} 而不是 {@code hasRole("ADMIN")}。
 */
public record AuthUser(Integer id, String username, String role) {
}
