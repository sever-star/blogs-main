package com.syt.blog.common;

public class DuplicateNameException extends BusinessException {
    public DuplicateNameException(String message) {
        super(400, message);
    }
}
