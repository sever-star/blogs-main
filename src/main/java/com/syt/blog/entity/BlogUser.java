package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 博客用户实体
 * <p>
 * 映射 blog_users 表（含 ALTER TABLE 扩展字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_users",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_username", columnNames = "username")
       })
public class BlogUser {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 用户名 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 昵称（ALTER TABLE 增加） */
    @Column(length = 50)
    private String nickname;

    /** 密码（加密存储） */
    @Column(nullable = false, length = 255)
    private String password;

    /** 邮箱 */
    @Column(nullable = false, length = 100)
    private String email;

    /** 头像 URL（ALTER TABLE 增加） */
    @Column(length = 255)
    private String avatar;

    /** 个人简介（ALTER TABLE 增加） */
    @Column(length = 200)
    private String bio;

    /** 账号状态：1-正常，0-禁用（ALTER TABLE 增加） */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /** 个人网站（ALTER TABLE 增加） */
    @Column(length = 255)
    private String website;

    /** GitHub 地址（ALTER TABLE 增加） */
    @Column(length = 255)
    private String github;

    /** 微博地址（ALTER TABLE 增加） */
    @Column(length = 255)
    private String weibo;

    /** 最后登录时间（ALTER TABLE 增加） */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** 注册时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间（ALTER TABLE 增加） */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.status == null) this.status = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
