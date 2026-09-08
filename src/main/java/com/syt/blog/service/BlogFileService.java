package com.syt.blog.service;

import org.springframework.web.multipart.MultipartFile;

public interface BlogFileService {
    String uploadFile(MultipartFile file);
}
