package com.syt.blog.controller;

import com.syt.blog.DTO.Mapper.CommentMapper;
import com.syt.blog.DTO.Request.PostCommentRequest;
import com.syt.blog.DTO.Response.CommentsResponse;
import com.syt.blog.common.Result;
import com.syt.blog.service.BlogPostCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class BlogPostCommentController {
    private final BlogPostCommentService blogPostCommentService;

    public BlogPostCommentController(BlogPostCommentService blogPostCommentService) {
        this.blogPostCommentService = blogPostCommentService;
    }

    /**
     * 获取文章评论列表
     */
    @GetMapping("/{articleId}/comments")
    public Result<List<CommentsResponse>> getComments(@PathVariable("articleId") Integer postId) {
        return Result.success(blogPostCommentService.listComments(postId));
    }

    /*
     * 创建评论
     */
    @PostMapping("/{articleId}/comments")
    public Result<CommentsResponse> createComment(@PathVariable("articleId") @Valid Integer postId, @Valid @RequestBody PostCommentRequest request) {
        return Result.success(blogPostCommentService.createComment(postId,request));
    }

    /**
     * 删除评论*/

    @DeleteMapping("/{articleId}/comments/{commentId}")
    public Result deleteComment(@PathVariable Integer commentId, @PathVariable("articleId") @NotNull Integer postId) {
        blogPostCommentService.deleteComment(commentId, postId);
        return Result.success();
    }

    /**
     * 点赞评论*/
    @PostMapping("/{articleId}/comments/{commentId}/like")
    public Result<Map<String,Integer>> likeComment(@PathVariable Integer commentId, @PathVariable("articleId")  Integer postId) {
        Map<String,Integer> map = new HashMap<>();
        map.put("likeCount",blogPostCommentService.likeComment(commentId, postId));
        return Result.success(map);
    }
    /**
     * 取消点赞评论*/
    @DeleteMapping("/{articleId}/comments/{commentId}/like")
    public Result<Map<String,Integer>> cancelLikeComment(@PathVariable Integer commentId, @PathVariable("articleId")  Integer postId) {
        Map<String,Integer> map = new HashMap<>();
        map.put("likeCount",blogPostCommentService.cancelLikeComment(commentId, postId));
        return Result.success(map);
    }
}