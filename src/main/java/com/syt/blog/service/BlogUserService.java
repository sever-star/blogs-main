package com.syt.blog.service;

import com.syt.blog.dto.LoginDTO;
import com.syt.blog.entity.BlogUser;
import com.syt.blog.Vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

public interface BlogUserService {

    UserVO login(LoginDTO loginDTO, HttpServletRequest request);

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param id 用户 ID
     * @return 用户信息（不含密码）
     */
    UserVO getUser(Integer id);

    /**
     * 注册
     */
    UserVO register(BlogUser blogUser);

    BlogUser update(String token, BlogUser blogUser);

}