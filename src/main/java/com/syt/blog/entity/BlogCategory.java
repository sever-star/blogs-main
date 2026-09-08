package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章分类实体
 * <p>
 * 映射 blog_categories 表，支持无限极分类（parent_id）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_categories")
public class BlogCategory {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 分类名称 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 父分类 ID，0 代表顶级分类 */
    @Column(name = "parent_id", columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer parentId;

    /** 排序序号 */
    @Column(name = "sort_order", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.parentId == null) this.parentId = 0;
        if (this.sortOrder == null) this.sortOrder = 0;
    }
}
