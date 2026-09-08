package com.syt.blog.controller;


import com.syt.blog.Vo.TagResponse;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.dto.TagUpdateDTO;
import com.syt.blog.entity.BlogTag;
import com.syt.blog.service.BlogTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
public class BlogTagController {

    private final BlogTagService blogTagService;

    @PostMapping
    public Result<BlogTag> createBlogTag(@Valid @RequestBody BlogTag blogTag) {
        BlogTag Tag = blogTagService.saveBlogTag(blogTag);
        if(Tag==null)return Result.error(400,"标签重复");
        return Result.success(Tag);
    }

    @GetMapping
    public Result<List<BlogTag>> getAllBlogTags() {
        List<BlogTag> blogTags = blogTagService.getAllBlogTags();
        return Result.success(blogTags);
    }
    @GetMapping("/{id}")
    public Result<BlogTag> getBlogTagById(@PathVariable Integer id) {
        BlogTag blogTag = blogTagService.getById(id);
        if (blogTag == null)return Result.error(404,"标签不存在");
        return Result.success(blogTag);
    }
    @PutMapping("/{id}")
    public Result<TagResponse> updateBlogTag(@PathVariable Integer id,
                                             @RequestBody @Valid TagUpdateDTO tagUpdateDTO){
        TagResponse tagResponse;
        tagResponse = blogTagService.updateBlogTag(id, tagUpdateDTO);
        return Result.success(tagResponse);
    }
}
