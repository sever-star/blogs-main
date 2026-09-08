package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章点赞实体
 * <p>
 * 映射 blog_post_likes 表，通过联合唯一索引实现游客/IP 与注册用户的防重点赞
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_post_likes",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_post_user", columnNames = {"post_id", "user_id"}),
           @UniqueConstraint(name = "uk_post_ip", columnNames = {"post_id", "user_ip"})
       })
public class BlogPostLike {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被点赞的文章 ID */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

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
