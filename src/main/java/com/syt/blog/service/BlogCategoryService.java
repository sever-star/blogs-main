package com.syt.blog.service;

import com.syt.blog.common.DuplicateNameException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.dto.CategoryDTO;
import com.syt.blog.entity.BlogCategory;
import com.syt.blog.repository.BlogCategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类业务逻辑实现类
 */
@Service
public class BlogCategoryService {

    private final BlogCategoryRepository blogCategoryRepository;

    public BlogCategoryService(BlogCategoryRepository blogCategoryRepository) {
        this.blogCategoryRepository = blogCategoryRepository;
    }

    @Transactional
    public BlogCategory createCategory(CategoryDTO categoryDTO) {
        if (blogCategoryRepository.existsByName(categoryDTO.getName())) {
            throw new DuplicateNameException("分类名称已存在: " + categoryDTO.getName());
        }
        BlogCategory blogCategory = new BlogCategory();
        toCategory(categoryDTO, blogCategory);
        return blogCategoryRepository.save(blogCategory);
    }

    /**
     * 获取全量分类（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     *
     * @param keyword 名称关键词，可为空
     * @return 分类全量列表
     */
    public List<BlogCategory> getAllCategories(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return blogCategoryRepository.findByNameContaining(keyword);
        }
        return blogCategoryRepository.findAll();
    }

    /**
     * 分页获取分类（供管理表格使用，支持 keyword 模糊搜索）。
     *
     * @param keyword  名称关键词，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult getCategoriesPaged(String keyword, Integer page, Integer pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<BlogCategory> result;
        if (keyword != null && !keyword.trim().isEmpty()) {
            result = blogCategoryRepository.findByNameContaining(keyword, pageable);
        } else {
            result = blogCategoryRepository.findAll(pageable);
        }
        return new PageResult(result.getContent(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    /**
     * 根据 ID 获取分类
     * @param id 分类 ID
     * @return 分类
     */
    public BlogCategory getCategoryById(Integer id) {
        return blogCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在，ID: " + id));
    }

    /**
     * 更新分类
     * @param id 分类 ID
     * @param categoryDTO 分类 DTO
     * @return 更新后的分类
     */
    @Transactional
    public BlogCategory updateCategory(Integer id, CategoryDTO categoryDTO) {
        BlogCategory blogCategory = blogCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在，ID: " + id));
        boolean exists = blogCategoryRepository.existsByNameAndIdNot(categoryDTO.getName(), id);
        if (exists) {
            throw new DuplicateNameException("分类名称已存在: " + categoryDTO.getName());
        }
        toCategory(categoryDTO, blogCategory);
        return blogCategoryRepository.save(blogCategory);
    }

    /**
     * 删除分类
     * @param id 分类 ID
     */
    @Transactional
    public void deleteCategory(Integer id) {
        if (!blogCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("分类不存在，ID: " + id);
        }
        blogCategoryRepository.deleteById(id);
    }
    private void toCategory(CategoryDTO categoryDTO, BlogCategory blogCategory){
        blogCategory.setName(categoryDTO.getName());
    }
}
