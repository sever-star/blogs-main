package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 消息明细实体
 * <p>
 * 映射 blog_ai_messages 表，记录会话上下文流水（Markdown 格式多轮对话）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_ai_messages")
public class BlogAiMessage {

    /** 消息主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 消息角色：user-用户提问，assistant-AI 回复 */
    @Column(nullable = false, length = 20)
    private String role;

    /** 消息内容（Markdown 格式） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 消耗的 Token 数（成本控制） */
    @Column(columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer tokens;

    /** 状态：1-成功，0-生成中，2-失败/违规 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.tokens == null) this.tokens = 0;
        if (this.status == null) this.status = 1;
    }
}
