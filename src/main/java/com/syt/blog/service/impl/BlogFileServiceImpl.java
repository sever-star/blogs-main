package com.syt.blog.service.impl;

import com.syt.blog.service.BlogFileService;
import com.syt.blog.util.OssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogFileServiceImpl implements BlogFileService {
    private final OssUtil ossUtil;

    @Override
    public String uploadFile(MultipartFile file) {
        return ossUtil.uploadImage(file, "avatar");
    }
}
