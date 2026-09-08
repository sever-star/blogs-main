package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站点统计实体
 * <p>
 * 映射 blog_statistics 表，缓存站点整体统计数据，避免频繁 COUNT 查询
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_statistics")
public class BlogStatistic {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 统计项键名（如 total_posts、total_comments 等） */
    @Column(name = "stat_key", nullable = false, unique = true, length = 50)
    private String statKey;

    /** 统计值 */
    @Column(name = "stat_value", nullable = false, columnDefinition = "BIGINT UNSIGNED DEFAULT 0")
    private Long statValue;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.statValue == null) this.statValue = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
