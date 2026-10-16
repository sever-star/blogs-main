package com.syt.blog.controller;

import com.syt.blog.DTO.Mapper.CommentMapper;
import com.syt.blog.DTO.Request.PostCommentRequest;
import com.syt.blog.DTO.Response.CommentsResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.jooq.tables.pojos.BlogComments;
import com.syt.blog.service.BlogPostCommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        BlogComments comment = CommentMapper.INSTANCE.createComment(request);
        return Result.success(blogPostCommentService.createComment(postId,comment));
    }

}