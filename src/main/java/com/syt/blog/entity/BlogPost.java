package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章内容实体
 * <p>
 * 映射 blog_posts 表（含 ALTER TABLE 扩展字段），适配 Markdown 编辑器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_posts")
public class BlogPost {

    /** 文章主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 作者 ID */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** 所属分类 ID */
    @Column(name = "category_id")
    private Integer categoryId;

    /** 文章标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** Markdown 原始内容 */
    @Column(name = "content_md", nullable = false, columnDefinition = "LONGTEXT")
    private String contentMd;

    /** 渲染后的 HTML 内容（冗余字段） */
    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    private String contentHtml;

    /** 文章摘要，AI 自动生成（ALTER TABLE 增加） */
    @Column(length = 500)
    private String summary;

    /** 自动生成的目录 HTML */
    @Column(name = "toc_html", columnDefinition = "TEXT")
    private String tocHtml;

    /** 文章封面图 URL */
    @Column(name = "cover_image", length = 255)
    private String coverImage;

    /** 浏览量 */
    @Column(name = "view_count", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer viewCount;

    /** 点赞数（冗余计数） */
    @Column(name = "like_count", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer likeCount;

    /** 收藏数（冗余计数） */
    @Column(name = "fav_count", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer favCount;

    /** 预计阅读时长，单位分钟（ALTER TABLE 增加） */
    @Column(name = "reading_time", columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Integer readingTime;

    /** 状态：2-审核 1-发布，0-草稿 */
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 2")
    private Integer status;

    /** 是否置顶：1-置顶，0-普通（ALTER TABLE 增加） */
    @Column(name = "is_top", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Integer isTop;

    /** 是否允许评论：1-允许，0-禁止（ALTER TABLE 增加） */
    @Column(name = "allow_comment", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Integer allowComment;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 软删除时间（ALTER TABLE 增加） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.viewCount == null) this.viewCount = 0;
        if (this.likeCount == null) this.likeCount = 0;
        if (this.favCount == null) this.favCount = 0;
        if (this.readingTime == null) this.readingTime = 0;
        if (this.status == null) this.status = 1;
        if (this.isTop == null) this.isTop = 0;
        if (this.allowComment == null) this.allowComment = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
