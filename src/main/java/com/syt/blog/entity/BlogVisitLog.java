package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站点访问日志实体
 * <p>
 * 映射 blog_visit_logs 表，记录访客 PV/UV 用于统计分析
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_visit_logs")
public class BlogVisitLog {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 访问的文章 ID（若为文章页） */
    @Column(name = "post_id")
    private Integer postId;

    /** 访问用户 ID（注册用户） */
    @Column(name = "user_id")
    private Integer userId;

    /** 访客 IP */
    @Column(name = "user_ip", nullable = false, length = 45)
    private String userIp;

    /** 浏览器 User-Agent */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** 来源页面 */
    @Column(length = 255)
    private String referer;

    /** 访问 URL */
    @Column(name = "visit_url", nullable = false, length = 255)
    private String visitUrl;

    /** 停留时长（秒） */
    @Column(name = "stay_duration", columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer stayDuration;

    /** 访问时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.stayDuration == null) this.stayDuration = 0;
    }
}
