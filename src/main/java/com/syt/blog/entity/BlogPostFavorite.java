package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章收藏实体
 * <p>
 * 映射 blog_post_favorites 表，记录用户的收藏动作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_post_favorites",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_post_user", columnNames = {"post_id", "user_id"})
       })
public class BlogPostFavorite {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被收藏的文章 ID */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /** 收藏用户 ID */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** 收藏时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
