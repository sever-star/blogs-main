package com.syt.blog.common;

public final class ErrorCode {

    private ErrorCode() {}

    public static final int SUCCESS = 200;

    public static final int TOKEN_EXPIRED = 401001;
    public static final int TOKEN_INVALID = 401002;
    public static final int TOKEN_MISSING = 401003;
    public static final int AUTH_FAILED = 401004;

    public static final int SERVER_ERROR = 500;
}