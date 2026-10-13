package com.syt.blog.util;

import com.syt.blog.common.AuthUser;
import com.syt.blog.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文取当前登录用户。
 * <p>
 * 迁移前各 Controller / Service 各自 {@code authorization.replace("Bearer ", "")} 解析一遍 token，
 * 改造后统一从这里取，避免重复解析与两套认证并存。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 当前登录用户；未认证返回 null，供「游客也可访问」的分支判断，不抛异常。
     * <p>
     * 注意 {@code AnonymousAuthenticationFilter} 也会塞一个已认证的匿名 Authentication，
     * 所以不能只看 {@code isAuthenticated()}，必须判断 principal 的实际类型。
     */
    //获取jwtFilter中存储的用户信息
    public static AuthUser currentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        //从authentication中获取用户信息
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthUser authUser ? authUser : null;
    }

    /**
     * 当前登录用户 ID；未登录抛 401。
     * <p>
     * 只应出现在「已声明 authenticated() 的路径」上，此时匿名请求已被 EntryPoint 拦下。
     */
    public static Integer currentUserId() {
        AuthUser user = currentUser();
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user.id();
    }

    /** 是否为博主/管理员 */
    public static boolean isAdmin() {
        AuthUser user = currentUser();
        return user != null && "ADMIN".equals(user.role());
    }
}
