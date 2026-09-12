package com.syt.blog.repository;

import com.syt.blog.entity.BlogTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogTagRepository extends JpaRepository<BlogTag,Integer> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Integer id);
    Page<BlogTag> findByNameContaining(String keyword, Pageable pageable );
}
