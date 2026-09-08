package com.syt.blog.service;

import com.syt.blog.entity.BlogPost;

import java.util.List;

/**
 * 文章业务逻辑接口
 */
public interface BlogPostService {

    /**
     * 新增文章
     */
    BlogPost createPost(BlogPost post);

    /**
     * 根据 ID 查询文章
     */
    BlogPost getPostById(Long id);

    /**
     * 查询所有已发布文章
     */
    List<BlogPost> getPublishedPosts();

    /**
     * 根据分类查询已发布文章
     */
    List<BlogPost> getPostsByCategory(Long categoryId);

    /**
     * 模糊搜索文章
     */
    List<BlogPost> searchPosts(String keyword);

    /**
     * 更新文章
     */
    BlogPost updatePost(Long id, BlogPost post);

    /**
     * 删除文章（软删除）
     */
    void deletePost(Long id);

    /**
     * 发布文章（将草稿状态改为已发布）
     */
    BlogPost publishPost(Long id);

    /**
     * 增加文章浏览量
     */
    void incrementViewCount(Long id);
}
