package com.syt.blog.controller;

import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;

import com.syt.blog.service.BlogFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class BlogFileController {

    private final BlogFileService blogFileService;

    @PostMapping("/avatar")
    public Result<UploadResult> uploadFile(@RequestHeader("Authorization") String authorization, @RequestParam("file") MultipartFile file) {
        String url;
        try {
            url=blogFileService.uploadFile(file);
        }catch (Exception e){
            return Result.error(ErrorCode.AUTH_FAILED, e.getMessage());
        }
        return Result.success(new UploadResult(url));
    }


    private record  UploadResult(String url){}
}
