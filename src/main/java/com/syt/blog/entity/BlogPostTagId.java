package com.syt.blog.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * 文章-标签关联表复合主键
 * <p>
 * 用于 {@link BlogPostTag} 的 @IdClass
 */
public class BlogPostTagId implements Serializable {

    private Integer postId;
    private Integer tagId;

    public BlogPostTagId() {}

    public BlogPostTagId(Integer postId, Integer tagId) {
        this.postId = postId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlogPostTagId that = (BlogPostTagId) o;
        return Objects.equals(postId, that.postId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, tagId);
    }
}
