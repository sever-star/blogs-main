package com.syt.blog.repository;

import com.syt.blog.entity.BlogTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlogTagRepository extends JpaRepository<BlogTag,Integer> {
    /**
     * 判断名称是否已存在
     * @param name
     * @return
     */
    boolean existsByName(String name);
    /**
     * 判断名称是否已存在且不等于当前id
     * @param name
     * @param id
     * @return
     */
    boolean existsByNameAndIdNot(String name, Integer id);
    /**
     * 根据名称查询
     * @param keyword
     * @param pageable
     * @return
     */
    Page<BlogTag> findByNameContaining(String keyword, Pageable pageable );

    /**
     * 根据名称模糊查询（不分页，供选择器使用）
     * @param keyword 关键词
     * @return 标签列表
     */
    List<BlogTag> findByNameContaining(String keyword);

    /**
     * 根据名称查询
     * @param name
     * @return
     */
    BlogTag findByName(String name);
}
