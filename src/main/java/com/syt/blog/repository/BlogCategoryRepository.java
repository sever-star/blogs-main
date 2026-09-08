package com.syt.blog.repository;

import com.syt.blog.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分类数据访问层
 */
@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Long> {

    /**
     * 根据分类名称查询
     *
     * @param name 分类名称
     * @return 分类实体（Optional 包装）
     */
    Optional<BlogCategory> findByName(String name);

    /**
     * 判断分类名称是否已存在
     *
     * @param name 分类名称
     * @return true 表示已存在
     */
    boolean existsByName(String name);

    /**
     * 根据父分类 ID 查询子分类
     *
     * @param parentId 父分类 ID
     * @return 子分类列表
     */
    List<BlogCategory> findByParentId(Long parentId);
}
