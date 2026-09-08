package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索关键词热度实体
 * <p>
 * 映射 blog_search_keywords 表，记录用户搜索关键词，分析用户兴趣
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_search_keywords")
public class BlogSearchKeyword {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 搜索关键词 */
    @Column(nullable = false, unique = true, length = 100)
    private String keyword;

    /** 搜索次数 */
    @Column(name = "search_count", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 1")
    private Integer searchCount;

    /** 最后搜索时间 */
    @Column(name = "last_search_at", nullable = false)
    private LocalDateTime lastSearchAt;

    @PrePersist
    protected void onCreate() {
        if (this.lastSearchAt == null) this.lastSearchAt = LocalDateTime.now();
        if (this.searchCount == null) this.searchCount = 1;
    }
}
