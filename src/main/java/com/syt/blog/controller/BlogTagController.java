package com.syt.blog.controller;


import com.syt.blog.DTO.Mapper.TagMapper;
import com.syt.blog.DTO.Response.TagResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.DTO.Request.TagRequest;

import com.syt.blog.jooq.tables.pojos.BlogTags;
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

    /**
     * 创建标签
     */
    @PostMapping
    public Result<TagResponse> createBlogTag(@Valid @RequestBody TagRequest tagRequest) {
        BlogTags blogTag = TagMapper.INSTANCE.blogTagsToBlogTagRequest(tagRequest);
        TagResponse tagResponse = blogTagService.saveBlogTag(blogTag);
        return Result.success(tagResponse);
    }

    /**
     * 获取全量标签（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     */
    @GetMapping
    public Result<List<TagResponse>> getAllBlogTags(@RequestParam(required = false) String keyword) {
        List<TagResponse> tags = blogTagService.getAllBlogTags(keyword);
        return Result.success(tags);
    }

    /**
     * 分页获取标签（供管理表格使用，支持 keyword 模糊搜索）。
     */
    @GetMapping("/page")
    public Result<PageResult> getBlogTagsPaged(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = blogTagService.getBlogTagsPaged(keyword, page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 根据id获取标签
     */
    @GetMapping("/{id}")
    public Result<TagResponse> getBlogTagById(@PathVariable Integer id) {
        TagResponse tagResponse = blogTagService.getById(id);
        return Result.success(tagResponse);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public Result<TagResponse> updateBlogTag(@PathVariable Integer id,
                                             @RequestBody @Valid TagRequest tagRequest){
        BlogTags blogTags= TagMapper.INSTANCE.blogTagsToBlogTagRequest(tagRequest);
        TagResponse tagResponse = blogTagService.updateBlogTag(id, blogTags);
        return Result.success(tagResponse);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public Result deleteBlogTag(@PathVariable Integer id) {
        blogTagService.deleteBlogTag(id);
        return Result.success();
    }
}
