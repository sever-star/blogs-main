package com.syt.blog.service;

import com.syt.blog.entity.BlogCategory;
import com.syt.blog.repository.BlogCategoryRepository;
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
    public BlogCategory createCategory(BlogCategory category) {
        if (blogCategoryRepository.existsByName(category.getName())) {
            throw new RuntimeException("分类名称已存在: " + category.getName());
        }
        return blogCategoryRepository.save(category);
    }

    
    public List<BlogCategory> getAllCategories() {
        return blogCategoryRepository.findAll();
    }

    
    public List<BlogCategory> getTopCategories() {
        return blogCategoryRepository.findByParentId(0L);
    }

    
    public List<BlogCategory> getSubCategories(Long parentId) {
        return blogCategoryRepository.findByParentId(parentId);
    }

    
    public BlogCategory getCategoryById(Long id) {
        return blogCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在，ID: " + id));
    }

    
    @Transactional
    public BlogCategory updateCategory(Long id, BlogCategory category) {
        BlogCategory existing = getCategoryById(id);
        if (category.getName() != null && !category.getName().equals(existing.getName())) {
            if (blogCategoryRepository.existsByName(category.getName())) {
                throw new RuntimeException("分类名称已存在: " + category.getName());
            }
            existing.setName(category.getName());
        }
        if (category.getParentId() != null) existing.setParentId(category.getParentId());
        if (category.getSortOrder() != null) existing.setSortOrder(category.getSortOrder());
        return blogCategoryRepository.save(existing);
    }

    
    @Transactional
    public void deleteCategory(Long id) {
        if (!blogCategoryRepository.existsById(id)) {
            throw new RuntimeException("分类不存在，ID: " + id);
        }
        blogCategoryRepository.deleteById(id);
    }
}
