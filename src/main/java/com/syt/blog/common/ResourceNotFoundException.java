package com.syt.blog.common;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(404, message);
    }
    public class DuplicateNameException extends BusinessException {
        public DuplicateNameException(String message) {
            super(400, message);
        }
    }
}

