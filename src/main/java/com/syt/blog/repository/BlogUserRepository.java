package com.syt.blog.repository;

import com.syt.blog.entity.BlogUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogUserRepository extends JpaRepository<BlogUser, Integer> {
    /**
     * 根据用户名查询用户
     * @param username
     * @return
     */
    BlogUser findByUsername(String username);
}
