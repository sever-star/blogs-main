package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 友情链接实体
 * <p>
 * 映射 blog_links 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_links")
public class BlogLink {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 站点名称 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 站点 URL */
    @Column(nullable = false, length = 255)
    private String url;

    /** 站点 Logo URL */
    @Column(length = 255)
    private String logo;

    /** 站点描述 */
    @Column(length = 200)
    private String description;

    /** 排序序号（数字越大越靠前） */
    @Column(name = "sort_order", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder;

    /** 状态：1-显示，0-隐藏 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1")
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
        if (this.sortOrder == null) this.sortOrder = 0;
        if (this.status == null) this.status = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
