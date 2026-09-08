package com.syt.blog.service;

import com.syt.blog.entity.BlogCategory;

import java.util.List;

/**
 * 分类业务逻辑接口
 */
public interface BlogCategoryService {

    /**
     * 新增分类
     */
    BlogCategory createCategory(BlogCategory category);

    /**
     * 查询所有分类
     */
    List<BlogCategory> getAllCategories();

    /**
     * 查询所有顶级分类
     */
    List<BlogCategory> getTopCategories();

    /**
     * 根据父分类 ID 查询子分类
     */
    List<BlogCategory> getSubCategories(Long parentId);

    /**
     * 根据 ID 查询分类
     */
    BlogCategory getCategoryById(Long id);

    /**
     * 更新分类
     */
    BlogCategory updateCategory(Long id, BlogCategory category);

    /**
     * 删除分类
     */
    void deleteCategory(Long id);
}
