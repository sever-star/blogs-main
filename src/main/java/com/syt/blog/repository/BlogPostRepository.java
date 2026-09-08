package com.syt.blog.repository;

import com.syt.blog.entity.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章数据访问层
 */
@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long>, JpaSpecificationExecutor<BlogPost> {

    /**
     * 根据状态查询文章列表
     *
     * @param status 文章状态（1-发布，0-草稿）
     * @return 文章列表
     */
    List<BlogPost> findByStatus(Integer status);

    /**
     * 根据分类 ID 和状态查询文章
     *
     * @param categoryId 分类 ID
     * @param status     文章状态
     * @return 文章列表
     */
    List<BlogPost> findByCategoryIdAndStatus(Long categoryId, Integer status);

    /**
     * 根据标题模糊搜索（按创建时间倒序）
     *
     * @param title 标题关键词
     * @return 文章列表
     */
    List<BlogPost> findByTitleContainingOrderByCreatedAtDesc(String title);

    /**
     * 根据用户 ID 查询
     *
     * @param userId 用户 ID
     * @return 文章列表
     */
    List<BlogPost> findByUserId(Long userId);
}
