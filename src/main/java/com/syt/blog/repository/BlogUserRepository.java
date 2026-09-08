package com.syt.blog.repository;

import com.syt.blog.entity.BlogUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogUserRepository extends JpaRepository<BlogUser, Integer> {
    BlogUser findByUsername(String username);
}
