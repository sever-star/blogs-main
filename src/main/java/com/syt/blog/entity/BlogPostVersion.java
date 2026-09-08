package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章版本历史实体
 * <p>
 * 映射 blog_post_versions 表，记录文章编辑历史，支持版本回滚
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_post_versions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_post_version", columnNames = {"post_id", "version"})
       })
public class BlogPostVersion {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 文章 ID */
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /** 版本号（从 1 开始递增） */
    @Column(nullable = false)
    private Integer version;

    /** 文章标题（历史版本） */
    @Column(nullable = false, length = 200)
    private String title;

    /** Markdown 内容（历史版本） */
    @Column(name = "content_md", nullable = false, columnDefinition = "LONGTEXT")
    private String contentMd;

    /** HTML 内容（历史版本） */
    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    private String contentHtml;

    /** 版本变更说明 */
    @Column(name = "change_note", length = 200)
    private String changeNote;

    /** 版本创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
