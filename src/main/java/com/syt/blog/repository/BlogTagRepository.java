package com.syt.blog.repository;

import com.syt.blog.entity.BlogTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogTagRepository extends JpaRepository<BlogTag,Integer> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Integer id);
}
