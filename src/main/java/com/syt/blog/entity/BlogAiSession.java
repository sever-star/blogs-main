package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 会话实体
 * <p>
 * 映射 blog_ai_sessions 表，管理网页版 AI 的多对话窗口及历史列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_ai_sessions")
public class BlogAiSession {

    /** 会话主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID（注册用户） */
    @Column(name = "user_id")
    private Integer userId;

    /** 用户 IP（游客标识） */
    @Column(name = "user_ip", nullable = false, length = 45)
    private String userIp;

    /** 会话标题 */
    @Column(nullable = false, length = 150, columnDefinition = "VARCHAR(150) DEFAULT '新的对话'")
    private String title;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.title == null) this.title = "新的对话";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
