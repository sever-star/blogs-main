package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章定时发布实体
 * <p>
 * 映射 blog_scheduled_posts 表，支持文章定时发布功能
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_scheduled_posts")
public class BlogScheduledPost {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 文章 ID（草稿状态） */
    @Column(name = "post_id", nullable = false, unique = true)
    private Integer postId;

    /** 计划发布时间 */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 状态：0-待发布，1-已发布，2-已取消 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer status;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.status == null) this.status = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
