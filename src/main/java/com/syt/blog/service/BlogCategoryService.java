package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.CategoryMapper;
import com.syt.blog.DTO.Response.CategoryResponse;
import com.syt.blog.common.DuplicateNameException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.jooq.tables.daos.BlogCategoriesDao;
import com.syt.blog.jooq.tables.pojos.BlogCategories;
import com.syt.blog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类业务逻辑实现类
 */
@Service
public class BlogCategoryService {

    private final CategoryRepository categoryRepository;
    private final BlogCategoriesDao blogCategoriesDao;

    public BlogCategoryService(CategoryRepository categoryRepository, BlogCategoriesDao blogCategoriesDao) {
        this.categoryRepository = categoryRepository;
        this.blogCategoriesDao = blogCategoriesDao;
    }

    @Transactional
    public CategoryResponse createCategory(BlogCategories blogCategories) {
        if (categoryRepository.existsByName(blogCategories.getName())) {
            throw new DuplicateNameException("分类名称已存在: " + blogCategories.getName());
        }
         blogCategoriesDao.insert(blogCategories);
        CategoryResponse categoryResponse = CategoryMapper.INSTANCE.blogCategoriesToCategoryResponse(blogCategories);
        return categoryResponse;
    }

    /**
     * 获取全量分类（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     *
     * @param keyword 名称关键词，可为空
     * @return 分类全量列表
     */
    public List<CategoryResponse> getAllCategories(String keyword) {
        List<BlogCategories> blogCategories;
        if (keyword != null && !keyword.trim().isEmpty()) {
            blogCategories=  blogCategoriesDao.fetchByName(keyword);
        }else{
            blogCategories =blogCategoriesDao.findAll();
        }

        List<CategoryResponse> categoryResponses=new ArrayList<>();
        for (BlogCategories category : blogCategories) {
            categoryResponses.add(CategoryMapper.INSTANCE.blogCategoriesToCategoryResponse(category));
        }
        return categoryResponses;
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
        PageResult result;
        if (keyword != null && !keyword.trim().isEmpty()) {
            result = categoryRepository.findPaged(page,pageSize,keyword);
        } else {
            result = categoryRepository.findByNameContaining(page, pageSize);
        }
        return result;
    }

    /**
     * 根据 ID 获取分类
     * @param id 分类 ID
     * @return 分类
     */
    public CategoryResponse getCategoryById(Integer id) {
        BlogCategories blogCategories = blogCategoriesDao.findById(id);
       if (blogCategories == null) {
           throw new ResourceNotFoundException("分类不存在，ID: " + id);
       }
        return CategoryMapper.INSTANCE.blogCategoriesToCategoryResponse(blogCategories);
    }

    /**
     * 更新分类
     * @param id 分类 ID
     * @param blogCategories 分类 DTO
     * @return 更新后的分类
     */
    @Transactional
    public CategoryResponse updateCategory(Integer id, BlogCategories blogCategories) {
        BlogCategories blogCategory = blogCategoriesDao.findById(id);
        if (blogCategory == null) {
            throw new ResourceNotFoundException("分类不存在，ID: " + id);
        }
        boolean exists = categoryRepository.existsByNameAndIdNot(blogCategories.getName(), id);
        if (exists) {
            throw new DuplicateNameException("分类名称已存在: " + blogCategories.getName());
        }
        blogCategories.setId(id);
         blogCategory=categoryRepository.update(blogCategories);
        return CategoryMapper.INSTANCE.blogCategoriesToCategoryResponse(blogCategory);
    }

    /**
     * 删除分类
     * @param id 分类 ID
     */
    @Transactional
    public void deleteCategory(Integer id) {
        if (!blogCategoriesDao.existsById(id)) {
            throw new ResourceNotFoundException("分类不存在，ID: " + id);
        }
        blogCategoriesDao.deleteById(id);
    }
}
