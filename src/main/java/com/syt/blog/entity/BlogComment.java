package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章评论实体
 * <p>
 * 映射 blog_comments 表，支持无限极盖楼回复及双用户模式（游客/会员）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_comments")
public class BlogComment {

    /** 评论主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属文章 ID */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /** 父评论 ID，0 代表对文章的直接评论 */
    @Column(name = "parent_id", columnDefinition = "BIGINT UNSIGNED DEFAULT 0")
    private Long parentId;

    /** 回复目标评论 ID，0 代表直接回复父评论 */
    @Column(name = "reply_to_id", columnDefinition = "BIGINT UNSIGNED DEFAULT 0")
    private Long replyToId;

    /** 评论用户 ID（已注册用户） */
    @Column(name = "user_id")
    private Integer userId;

    /** 评论者昵称（游客或会员） */
    @Column(nullable = false, length = 50)
    private String nickname;

    /** 评论者邮箱（接收回复通知） */
    @Column(length = 100)
    private String email;

    /** 评论者头像 URL */
    @Column(length = 255)
    private String avatar;

    /** 评论正文内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 是否博主/管理员回复：1-是，0-否 */
    @Column(name = "is_admin", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Integer isAdmin;

    /** 审核状态：0-待审核/默认不显示，1-通过/显示 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer status;

    /** 评论时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.parentId == null) this.parentId = 0L;
        if (this.replyToId == null) this.replyToId = 0L;
        if (this.isAdmin == null) this.isAdmin = 0;
        if (this.status == null) this.status = 0;
    }
}
