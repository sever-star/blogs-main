package com.syt.blog.common;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(404, message);
    }
}

