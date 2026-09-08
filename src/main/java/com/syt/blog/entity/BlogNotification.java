package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知实体
 * <p>
 * 映射 blog_notifications 表，评论回复通知、点赞通知等
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_notifications")
public class BlogNotification {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户 ID */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 通知类型
     * comment_reply — 评论回复
     * post_like     — 文章点赞
     * comment_like  — 评论点赞
     * system        — 系统通知
     */
    @Column(nullable = false, length = 30)
    private String type;

    /** 通知标题 */
    @Column(nullable = false, length = 100)
    private String title;

    /** 通知内容 */
    @Column(nullable = false, length = 500)
    private String content;

    /** 跳转链接 */
    @Column(length = 255)
    private String link;

    /** 是否已读：0-未读，1-已读 */
    @Column(name = "is_read", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Integer isRead;

    /** 通知时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.isRead == null) this.isRead = 0;
    }
}
