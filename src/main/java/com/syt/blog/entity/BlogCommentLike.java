package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论点赞实体
 * <p>
 * 映射 blog_comment_likes 表，针对评论维度进行防重控制
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_comment_likes",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_comment_user", columnNames = {"comment_id", "user_id"}),
           @UniqueConstraint(name = "uk_comment_ip", columnNames = {"comment_id", "user_ip"})
       })
public class BlogCommentLike {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被点赞的评论 ID */
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    /** 点赞用户 ID（注册用户） */
    @Column(name = "user_id")
    private Integer userId;

    /** 点赞用户 IP（游客去重） */
    @Column(name = "user_ip", length = 45)
    private String userIp;

    /** 点赞时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
