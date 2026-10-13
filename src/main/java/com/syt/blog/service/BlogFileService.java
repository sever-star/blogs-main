package com.syt.blog.service;

import com.syt.blog.util.OssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogFileService {
    private final OssUtil ossUtil;

    /**
     * 上传文件
     */
    public String uploadFile(MultipartFile file) {
        return ossUtil.uploadImage(file, "avatar");
    }

    /**
     * 上传文章封面图（存 OSS 的 cover 目录，与头像分开）
     */
    public String uploadCover(MultipartFile file) {
        return ossUtil.uploadImage(file, "cover");
    }
}
