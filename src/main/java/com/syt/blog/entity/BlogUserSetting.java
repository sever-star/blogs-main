package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户配置实体
 * <p>
 * 映射 blog_user_settings 表，用户个性化配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_user_settings")
public class BlogUserSetting {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    /** 主题样式 */
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'default'")
    private String theme;

    /** 语言偏好 */
    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'zh-CN'")
    private String language;

    /** 评论回复通知：1-开启，0-关闭 */
    @Column(name = "comment_notify", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Integer commentNotify;

    /** 点赞通知：1-开启，0-关闭 */
    @Column(name = "like_notify", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Integer likeNotify;

    /** 邮件通知：1-开启，0-关闭 */
    @Column(name = "email_notify", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Integer emailNotify;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.theme == null) this.theme = "default";
        if (this.language == null) this.language = "zh-CN";
        if (this.commentNotify == null) this.commentNotify = 1;
        if (this.likeNotify == null) this.likeNotify = 1;
        if (this.emailNotify == null) this.emailNotify = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
