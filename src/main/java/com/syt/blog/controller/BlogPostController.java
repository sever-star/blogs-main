package com.syt.blog.controller;

import com.syt.blog.common.Result;
import com.syt.blog.entity.BlogPost;
import com.syt.blog.service.BlogPostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章控制器
 * <p>
 * RESTful 风格接口，路径前缀 /api/posts
 */
@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostService blogPostService;

    public BlogPostController(BlogPostService blogPostService) {
        this.blogPostService = blogPostService;
    }

    // ==================== 新增文章 ====================

    /**
     * 创建文章
     */
    @PostMapping
    public Result<BlogPost> createPost(@RequestBody BlogPost post) {
        BlogPost saved = blogPostService.createPost(post);
        return Result.success(saved);
    }

    // ==================== 查询文章 ====================

    /**
     * 根据 ID 查询文章
     */
    @GetMapping("/{id}")
    public Result<BlogPost> getPost(@PathVariable Long id) {
        BlogPost post = blogPostService.getPostById(id);
        return Result.success(post);
    }

    /**
     * 查询所有已发布文章（公开列表）
     */
    @GetMapping("/published")
    public Result<List<BlogPost>> getPublishedPosts() {
        List<BlogPost> posts = blogPostService.getPublishedPosts();
        return Result.success(posts);
    }

    /**
     * 根据分类查询已发布文章
     */
    @GetMapping("/category/{categoryId}")
    public Result<List<BlogPost>> getPostsByCategory(@PathVariable Long categoryId) {
        List<BlogPost> posts = blogPostService.getPostsByCategory(categoryId);
        return Result.success(posts);
    }

    /**
     * 搜索文章
     */
    @GetMapping("/search")
    public Result<List<BlogPost>> searchPosts(@RequestParam String keyword) {
        List<BlogPost> posts = blogPostService.searchPosts(keyword);
        return Result.success(posts);
    }

    // ==================== 更新文章 ====================

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    public Result<BlogPost> updatePost(@PathVariable Long id, @RequestBody BlogPost post) {
        BlogPost updated = blogPostService.updatePost(id, post);
        return Result.success(updated);
    }

    /**
     * 发布文章
     */
    @PutMapping("/{id}/publish")
    public Result<BlogPost> publishPost(@PathVariable Long id) {
        BlogPost published = blogPostService.publishPost(id);
        return Result.success(published);
    }

    // ==================== 删除文章 ====================

    /**
     * 删除文章（软删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        blogPostService.deletePost(id);
        return Result.success();
    }

    // ==================== 浏览量 ====================

    /**
     * 增加文章浏览量
     */
    @PutMapping("/{id}/view")
    public Result<Void> incrementViewCount(@PathVariable Long id) {
        blogPostService.incrementViewCount(id);
        return Result.success();
    }
}
