package com.syt.blog.controller;


import com.syt.blog.Vo.TagResponse;
import com.syt.blog.common.Result;
import com.syt.blog.dto.TagUpdateDTO;
import com.syt.blog.entity.BlogTag;
import com.syt.blog.service.BlogTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
public class BlogTagController {

    private final BlogTagService blogTagService;

    @PostMapping
    public Result<BlogTag> createBlogTag(@Valid @RequestBody BlogTag blogTag) {
        BlogTag Tag = blogTagService.saveBlogTag(blogTag);
        return Result.success(Tag);
    }

    @GetMapping
    public Result<Object> getAllBlogTags(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer pageSize) {
        Object data = blogTagService.getAllBlogTags(keyword, page, pageSize);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<BlogTag> getBlogTagById(@PathVariable Integer id) {
        BlogTag blogTag = blogTagService.getById(id);
        return Result.success(blogTag);
    }

    @PutMapping("/{id}")
    public Result<TagResponse> updateBlogTag(@PathVariable Integer id,
                                             @RequestBody @Valid TagUpdateDTO tagUpdateDTO){
        TagResponse tagResponse;
        tagResponse = blogTagService.updateBlogTag(id, tagUpdateDTO);
        return Result.success(tagResponse);
    }

    @DeleteMapping("/tags/{id}")
    public Result deleteBlogTag(@PathVariable Integer id) {
        blogTagService.deleteBlogTag(id);
        return Result.success();
    }
}
