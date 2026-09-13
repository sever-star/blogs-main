package com.syt.blog.repository;

import com.syt.blog.entity.BlogCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分类数据访问层
 */
@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Integer> {

    /**
     * 根据分类名称查询
     *
     * @param name 分类名称
     * @return 分类实体（Optional 包装）
     */
    Optional<BlogCategory> findByName(String name);


    boolean existsByNameAndIdNot(String name, Integer id);
    /**
     * 判断分类名称是否已存在
     *
     * @param name 分类名称
     * @return true 表示已存在
     */
    boolean existsByName(String name);

    /**
     * 根据分类名称模糊查询
     *
     * @param name   分类名称
     * @param pageable 分页参数
     * @return 分类列表
     */
    Page<BlogCategory> findByNameContaining(String name, Pageable pageable);

    /**
     * 根据分类名称模糊查询（不分页，供选择器使用）
     *
     * @param name 分类名称关键词
     * @return 分类列表
     */
    List<BlogCategory> findByNameContaining(String name);
}
