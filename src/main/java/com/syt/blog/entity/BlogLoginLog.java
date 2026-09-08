package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户登录日志实体
 * <p>
 * 映射 blog_login_logs 表，记录用户登录历史，用于安全分析
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_login_logs")
public class BlogLoginLog {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户 ID */
    @Column(name = "user_id")
    private Integer userId;

    /** 登录用户名（冗余字段） */
    @Column(length = 50)
    private String username;

    /** 登录方式：1-账号密码，2-手机验证码，3-第三方登录 */
    @Column(name = "login_type", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer loginType;

    /** 登录 IP */
    @Column(nullable = false, length = 45)
    private String ip;

    /** 浏览器信息 */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** 登录状态：1-成功，0-失败 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /** 失败原因 */
    @Column(name = "fail_reason", length = 100)
    private String failReason;

    /** 登录时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.loginType == null) this.loginType = 1;
        if (this.status == null) this.status = 1;
    }
}
