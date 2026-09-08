package com.syt.blog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章-标签关联实体
 * <p>
 * 映射 blog_post_tags 多对多关联表，使用联合主键
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blog_post_tags")
@IdClass(BlogPostTagId.class)
public class BlogPostTag {

    /** 文章 ID */
    @Id
    @Column(name = "post_id", nullable = false)
    private Integer postId;

    /** 标签 ID */
    @Id
    @Column(name = "tag_id", nullable = false)
    private Integer tagId;
}
