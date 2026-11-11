package com.syt.blog.controller;

import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.DTO.Response.PostRevisionResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;

import com.syt.blog.service.BlogPostService;
import com.syt.blog.service.PostRevisionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章控制器
 * <p>
 * RESTful 风格接口，路径前缀 /api/posts
 */
@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostService blogPostService;
    private final PostRevisionService PostRevisionService;
//    private final PostMapper postMapper;

    public BlogPostController(BlogPostService blogPostService, PostRevisionService PostRevisionService) {
        this.blogPostService = blogPostService;
//        this.postMapper = postMapper;
        this.PostRevisionService = PostRevisionService;
    }

    // ==================== 新增文章 ====================

    /**
     * 创建文章
     */
    @PostMapping
    public Result<PostResponse> createPost(@RequestBody @Valid PostRequest postRequest) {
        PostResponse saved = blogPostService.createPost(postRequest);
        return Result.success(saved);
    }

    // ==================== 查询文章 ====================

    /**
     * 根据 ID 查询文章
     */
    @GetMapping("/{id}")
    public Result<BlogPostDetailResponse> getPost(@PathVariable Integer id, HttpServletRequest request) {
        BlogPostDetailResponse post = blogPostService.getPostById(id, request);
        return Result.success(post);
    }

    /**
     * 查询所有已发布文章（公开列表）
     */
    @GetMapping
    public Result<List<PostResponse>> getPublishedPosts(@RequestParam(required = false) Integer categoryId,
                                                        @RequestParam(required = false) Integer tagId) {
        List<PostResponse> posts = blogPostService.getPublishedPosts(categoryId, tagId);
        return Result.success(posts);
    }

    /**
     *查询登录用户点赞的文章
     */
    @GetMapping("/liked")
    public  Result<List<PostResponse>> getLikedPosts(){
        List<PostResponse> posts=blogPostService.getLikePost();
        return Result.success(posts);
    }
    @GetMapping("/favorited")
    public  Result<List<PostResponse>> getFavoritedPosts(){
        List<PostResponse> posts=blogPostService.getFavoritedPosts();
        return Result.success(posts);
    }
    /**
     * 查询所有待审核文章（仅管理员）
     */
    @GetMapping("/pending")
    public Result<PageResult> getPendingPosts(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = blogPostService.getPendingPosts(page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 我的文章（含草稿/待审核/已发布，仅本人，未登录 401）
     */
    @GetMapping("/mine")
    public Result<List<PostResponse>> getMyPosts() {
        return Result.success(blogPostService.getMyPosts());
    }

    /**
     * 浏览量前十
     * @param limit
     * @return
     */
    @GetMapping("/top")
    public Result<List<PostResponse>> getTopPosts(@RequestParam(defaultValue = "10") Integer limit){
        List<PostResponse> postResponses = blogPostService.getTopPosts(limit);
        return  Result.success(postResponses);
    }
    // ==================== 审核文章 ====================
    /**
     * 审核文章
     */
    @PutMapping("/{id}/approve")
    public Result approvePost(@PathVariable Integer id) {
        blogPostService.approvePost(id);
        return Result.success();
    }
    /**
     *
     * @param postId
     * @return
     */
    @GetMapping("/reviews/{postId}/ai-aid")
    public Result<Map<String,Object>> getAiAid(@PathVariable Long postId) {
        // status 单独读列，不能从 summary 是否为空推断（生成中/失败都可能是空）
        Short status = blogPostService.getReviewStatus(postId);
        String aid = blogPostService.getSummaryAid(postId);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String,Object> data = new HashMap<>();
        data.put("status", status == null ? 2 : status.intValue());
        // summary 有值才解析合并；为空保留 status（分析中/不可用由前端按 status 判断）
        if (aid != null && !aid.isEmpty()) {
            // 脏数据兜底：解析失败只返回 status，不让接口 500
            Map<String,Object> parsed = objectMapper.readValue(aid, Map.class);
            if (parsed != null) data.putAll(parsed);
        }
        return Result.success(data);
    }

    /**
     * 拒绝文章
     */
    @PutMapping("/{id}/reject")
    public Result rejectPost(@PathVariable Integer id, @RequestBody String reason) {
        blogPostService.rejectPost(id, reason);
        return Result.success();
    }

    /**
     * 下架文章
     * @param id
     * @return
     */
    @PutMapping("/{id}/offline")
    public Result offlinePost(@PathVariable Integer id) {
        blogPostService.offlinePost(id);
        return Result.success();
    }
    // ==================== 收藏文章 ====================
    /**
     * 收藏文章，返回操作后的收藏总数
     */
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favoritePost(@PathVariable("id") Integer postId) {
        Map<String, Object> map = new HashMap<>();
        map.put("favCount", blogPostService.favoritePost(postId));
        return Result.success(map);
    }

    /**
     * 取消收藏文章，返回操作后的收藏总数
     */
    @DeleteMapping("/{id}/favorite")
    public Result<Map<String, Object>> unfavoritePost(@PathVariable("id") Integer postId) {
        Map<String, Object> map = new HashMap<>();
        map.put("favCount", blogPostService.unfavoritePost(postId));
        return Result.success(map);
    }
    // ==================== 点赞文章 ====================
    /**
     * 点赞文章，返回操作后的点赞总数
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> likePost(@PathVariable("id") Integer postId) {
        Map<String, Object> map = new HashMap<>();
        map.put("likeCount", blogPostService.likePost(postId));
        return Result.success(map);
    }

    /*
     * 取消点赞文章，返回操作后的点赞总数
     */
    @DeleteMapping("/{id}/like")
    public Result<Map<String, Object>> unlikePost(@PathVariable("id") Integer postId) {
        Map<String, Object> map = new HashMap<>();
        map.put("likeCount", blogPostService.unlikePost(postId));
        return Result.success(map);
    }

    /**
     * 搜索文章
     */
    @GetMapping("/search")
    public Result<List<PostResponse>> searchPosts(@RequestParam String keyword) {
        return Result.success(blogPostService.searchPosts(keyword));
    }

    // ==================== 更新文章 ====================

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    public Result<PostResponse> updatePost(@PathVariable Integer id,
                                           @RequestBody @Valid PostRequest postRequest, HttpServletRequest request) {
        PostResponse updated = blogPostService.updatePost(id, postRequest,request);
        return Result.success(updated);
    }
    //==================== 版本控制 ====================

    /**
     * 获取文章所有版本
     * @param id
     * @return
     */
    @GetMapping("/{id}/revisions")
    public Result<List<PostRevisionResponse>> getPostRevisions(@PathVariable Long id) {
        return Result.success(PostRevisionService.getRevision(id));
    }

    /**
     * 获取文章指定版本
     * @param id
     * @return
     */
    @GetMapping("/{id}/revisions/{revId}")
    public Result<PostRevisionResponse> getPostRevision(@PathVariable Long id,
                                                              @PathVariable Long revId) {
        return Result.success(PostRevisionService.findRevision(id, revId));
    }


    /**
     * 回滚文章指定版本
     * @param id
     * @param revId
     * @return
     */
    @PostMapping("/{id}/revisions/{revId}/rollback")
    public Result<PostResponse> rollbackPost(@PathVariable Long id,
                                              @PathVariable Long revId) {
        PostResponse response = PostRevisionService.rollbackPost(id, revId);
        return Result.success(response);
    }

    // ==================== 删除文章 ====================

    /**
     * 删除文章（软删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Integer id) {
        blogPostService.deletePost(id);
        return Result.success();
    }
}




