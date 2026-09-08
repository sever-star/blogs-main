package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>
 * 映射 blog_operation_logs 表，记录管理员/用户的关键操作，便于审计追溯
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_operation_logs")
public class BlogOperationLog {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户 ID */
    @Column(name = "user_id")
    private Integer userId;

    /** 操作用户名（冗余字段，防止用户被删除后丢失） */
    @Column(length = 50)
    private String username;

    /** 操作动作（create_post、delete_comment、update_user 等） */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作对象类型（post、comment、user、category、tag） */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 操作对象 ID */
    @Column(name = "target_id", nullable = false, length = 50)
    private String targetId;

    /** 操作详情（JSON 格式存储变更前后数据） */
    @Column(columnDefinition = "JSON")
    private String detail;

    /** 操作 IP */
    @Column(length = 45)
    private String ip;

    /** 浏览器信息 */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** 操作时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
