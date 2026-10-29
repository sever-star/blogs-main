package com.syt.blog.common;

public final class ErrorCode {

    private ErrorCode() {}

    public static final int SUCCESS = 200;

    public static final int TOKEN_EXPIRED = 401001;
    public static final int TOKEN_INVALID = 401002;
    public static final int TOKEN_MISSING = 401003;
    public static final int AUTH_FAILED = 401004;
    public static final int ACCOUNT_LOGGED_IN_ELSEWHERE = 401005;

    /** 已认证但权限不足（角色不够，如 USER 调写接口）→ HTTP 403 */
    public static final int ACCESS_DENIED = 403001;
    /** 账号已被禁用（blog_users.status = 0）→ HTTP 403 */
    public static final int ACCOUNT_DISABLED = 403002;

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST =400;

    public static final int SERVER_ERROR = 500;

    /** 请求参数不合法（AI 提问内容为空、附件超限等） */
    public static final int AI_BAD_REQUEST = 400001;
    /** 提问过于频繁，超出频率限制 */
    public static final int AI_RATE_LIMITED = 429001;
    /** AI 服务不可用：未配置 Key、上游报错或超时 */
    public static final int AI_UNAVAILABLE = 503001;

    /**
     * 业务码是否映射为 HTTP 403。
     * <p>
     * {@link com.syt.blog.util.JwtAuthenticationEntryPoint} 也用它判断是否把 401 升级为 403
     * （如 token 有效但账号被禁用时返回 403 ACCOUNT_DISABLED）。
     */
    public static boolean isForbidden(int code) {
        return (code / 1000) == FORBIDDEN;
    }
}