package com.syt.blog.controller;

import com.syt.blog.common.Result;
import com.syt.blog.entity.BlogCategory;
import com.syt.blog.service.BlogCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类控制器
 * <p>
 * RESTful 风格接口，路径前缀 /api/categories
 */
@RestController
@RequestMapping("/api/categories")
public class BlogCategoryController {

    private final BlogCategoryService blogCategoryService;

    public BlogCategoryController(BlogCategoryService blogCategoryService) {
        this.blogCategoryService = blogCategoryService;
    }

    // ==================== 新增分类 ====================

    /**
     * 创建分类
     */
    @PostMapping
    public Result<BlogCategory> createCategory(@RequestBody BlogCategory category) {
        BlogCategory saved = blogCategoryService.createCategory(category);
        return Result.success(saved);
    }

    // ==================== 查询分类 ====================

    /**
     * 查询所有分类
     */
    @GetMapping
    public Result<List<BlogCategory>> getAllCategories() {
        List<BlogCategory> categories = blogCategoryService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 查询顶级分类
     */
    @GetMapping("/top")
    public Result<List<BlogCategory>> getTopCategories() {
        List<BlogCategory> categories = blogCategoryService.getTopCategories();
        return Result.success(categories);
    }

    /**
     * 查询子分类
     */
    @GetMapping("/sub/{parentId}")
    public Result<List<BlogCategory>> getSubCategories(@PathVariable Long parentId) {
        List<BlogCategory> categories = blogCategoryService.getSubCategories(parentId);
        return Result.success(categories);
    }

    /**
     * 根据 ID 查询分类
     */
    @GetMapping("/{id}")
    public Result<BlogCategory> getCategory(@PathVariable Long id) {
        BlogCategory category = blogCategoryService.getCategoryById(id);
        return Result.success(category);
    }

    // ==================== 更新分类 ====================

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    public Result<BlogCategory> updateCategory(@PathVariable Long id, @RequestBody BlogCategory category) {
        BlogCategory updated = blogCategoryService.updateCategory(id, category);
        return Result.success(updated);
    }

    // ==================== 删除分类 ====================

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        blogCategoryService.deleteCategory(id);
        return Result.success();
    }
}
