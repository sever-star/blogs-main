package com.syt.blog.controller;

import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.dto.CategoryDTO;
import com.syt.blog.entity.BlogCategory;
import com.syt.blog.service.BlogCategoryService;
import jakarta.validation.Valid;
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
    public Result<BlogCategory> createCategory(@RequestBody @Valid CategoryDTO categoryDTO) {
        BlogCategory saved = blogCategoryService.createCategory(categoryDTO);
        return Result.success(saved);
    }

    // ==================== 查询分类 ====================

    /**
     * 查询全量分类（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     */
    @GetMapping
    public Result<List<BlogCategory>> getAllCategories(@RequestParam(required = false) String keyword) {
        List<BlogCategory> categories = blogCategoryService.getAllCategories(keyword);
        return Result.success(categories);
    }

    /**
     * 分页查询分类（供管理表格使用，支持 keyword 模糊搜索）。
     */
    @GetMapping("/page")
    public Result<PageResult> getCategoriesPaged(@RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = blogCategoryService.getCategoriesPaged(keyword, page, pageSize);
        return Result.success(pageResult);
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
    public Result<BlogCategory> getCategory(@PathVariable Integer id) {
        BlogCategory category = blogCategoryService.getCategoryById(id);
        return Result.success(category);
    }

    // ==================== 更新分类 ====================

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    public Result<BlogCategory> updateCategory(@PathVariable Integer id, @RequestBody @Valid CategoryDTO categoryDTO) {
        BlogCategory updated = blogCategoryService.updateCategory(id, categoryDTO);
        return Result.success(updated);
    }

    // ==================== 删除分类 ====================

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Integer id) {
        blogCategoryService.deleteCategory(id);
        return Result.success();
    }
}
