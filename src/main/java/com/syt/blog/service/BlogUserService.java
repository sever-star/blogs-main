package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.UserMapper;
import com.syt.blog.DTO.Response.LoginResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.jooq.tables.daos.BlogUsersDao;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.DTO.Response.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLDataException;


@Service
@RequiredArgsConstructor
@Slf4j
public class BlogUserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final BlogUsersDao blogUsersDao;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 用户登录
     *
     * @param blogUsers 登录参数
     * @param request HTTP 请求
     * @return 登录结果
     */
    public LoginResponse login(BlogUsers blogUsers, HttpServletRequest request, HttpServletResponse response) {

        BlogUsers users = userRepository.findByUsername(blogUsers.getUsername());
        if (users == null || !bCryptPasswordEncoder.matches(blogUsers.getPassword(), users.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String accessToken = jwtUtils.generateToken(users.getId(), users.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(users.getId(), users.getUsername());

        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIp(request);
        refreshTokenService.saveRefreshToken(
                users.getId(), refreshToken, null, userAgent, ip
        );

        setRefreshTokenCookie(response, refreshToken, refreshExpiration);

        UserResponse userResponse = UserMapper.INSTANCE.BlogUsersToUserResponse(users);
        LoginResponse loginResponse = new LoginResponse(accessToken, userResponse);
        return loginResponse;
    }
    /**
     * 获取客户端 IP 地址
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取用户信息
     *
      * @param authorization 访问令牌
     * @return 用户信息
     */
    public UserResponse getUser(String authorization) {
        String token = authorization.replace("Bearer ", "");
        Integer userId = jwtUtils.getUserIdFromToken(token);
        BlogUsers user = blogUsersDao.findById(userId);
        if (user.getId() == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户不存在");
        }
        return UserMapper.INSTANCE.BlogUsersToUserResponse(user);
    }

    /**
     * 用户注册
     *
     * @param blogUsers 注册参数
     * @return 注册结果
     */
    @Transactional
    public LoginResponse register(BlogUsers blogUsers) {
        String username = blogUsers.getUsername();
        log.info("blogUsers user: " + username);

        BlogUsers existingUser = userRepository.findByUsername(username);
        log.info("Existing user: " + existingUser);

        if (existingUser != null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户已存在");
        }

        blogUsers.setPassword(bCryptPasswordEncoder.encode(blogUsers.getPassword()));
        blogUsers.setUsername(username);
        blogUsers.setEmail(blogUsers.getEmail());
        blogUsersDao.insert(blogUsers);

        log.info("userId: " + blogUsers.getId());
        String accessToken = jwtUtils.generateToken(blogUsers.getId(), blogUsers.getUsername());
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken(accessToken);
        UserResponse userResponse = UserMapper.INSTANCE.BlogUsersToUserResponse(blogUsers);
        loginResponse.setUser(userResponse);
        return loginResponse;
    }

    /**
     * 更新用户信息
     *
     * @param authorization    访问令牌
     * @param blogUsers 用户信息
     * @return 更新后的用户信息
     */
    @Transactional
    public UserResponse update(String authorization, BlogUsers blogUsers) {
        String token = authorization.replace("Bearer ", "");
        String username = jwtUtils.getUsernameFromToken(token);
        BlogUsers existing = userRepository.findByUsername(username);
        if (existing == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户不存在");
        }

        // 以 token 定位到的用户为准，防止越权修改他人信息，并保证主键正确
        blogUsers.setId(existing.getId());
        blogUsers.setEmail(existing.getEmail());
        // 用户名不允许修改
        blogUsers.setUsername(existing.getUsername());
        // 注册时间、状态不可通过此接口修改
        blogUsers.setCreatedAt(existing.getCreatedAt());
        blogUsers.setStatus(existing.getStatus());
        // 密码未传入时保留原密码，传入时加密
//        if (blogUsers.getPassword() == null || blogUsers.getPassword().isBlank()) {
            blogUsers.setPassword(existing.getPassword());
//        } else {
//            blogUsers.setPassword(bCryptPasswordEncoder.encode(blogUsers.getPassword()));
//        }

        blogUsersDao.update(blogUsers);
        return UserMapper.INSTANCE.BlogUsersToUserResponse(blogUsers);
    }
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
        clearRefreshTokenCookie(response);
    }
    /**
     * 清除刷新令牌 cookie
     *
     * @param response HTTP 响应
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 设置刷新令牌 cookie
     *
     * @param response HTTP 响应
     * @param token    刷新令牌
     * @param maxAgeMs 刷新令牌有效期（毫秒）
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMs) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (maxAgeMs / 1000));
        response.addCookie(cookie);
    }
}