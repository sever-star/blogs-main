package com.syt.blog.service;

import com.syt.blog.Vo.LoginResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.dto.LoginDTO;
import com.syt.blog.entity.BlogUser;
import com.syt.blog.repository.BlogUserRepository;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.Vo.UserVO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class BlogUserService {

    private final BlogUserRepository blogUserRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @param request HTTP 请求
     * @return 登录结果
     */
    public LoginResponse login(LoginDTO loginDTO, HttpServletRequest request, HttpServletResponse response) {
        BlogUser user = blogUserRepository.findByUsername(loginDTO.getUsername());

        if (user == null || !user.getPassword().equals(loginDTO.getPassword())) {
           throw new BusinessException(401, "用户名或密码错误");
        }
        String accessToken = jwtUtils.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIp(request);
        refreshTokenService.saveRefreshToken(
                user.getId(), refreshToken, null, userAgent, ip
        );

        setRefreshTokenCookie(response, refreshToken, refreshExpiration);

        UserVO userVO = toUserVO(user);
        LoginResponse loginResponse = new LoginResponse(accessToken, userVO);
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
     * @param id 用户ID
     * @return 用户信息
     */
    public UserVO getUser(Integer id) {
        return toUserVO(blogUserRepository.findById(id).orElse(null));
    }

    /**
     * 用户注册
     *
     * @param blogUser 用户信息
     * @return 注册结果
     */
    public UserVO register(BlogUser blogUser) {
        String user = blogUser.getUsername();
        log.info("Registering user: " + user);
        BlogUser existingUser = blogUserRepository.findByUsername(user);
        log.info("Existing user: " + existingUser);
        if (existingUser != null) {
            return null;
        }
        return toUserVO(blogUserRepository.save(blogUser));
    }

    /**
     * 更新用户信息
     *
     * @param token    访问令牌
     * @param blogUser 用户信息
     * @return 更新后的用户信息
     */
    public BlogUser update(String token, BlogUser blogUser) {
        String username=jwtUtils.getUsernameFromToken(token);
        BlogUser user = blogUserRepository.findByUsername(username);
        user.setNickname(blogUser.getNickname());
        user.setAvatar(blogUser.getAvatar());
        user.setBio(blogUser.getBio());
        user.setWebsite(blogUser.getWebsite());
        user.setGithub(blogUser.getGithub());
        user.setWeibo(blogUser.getWeibo());

        if (user != null){
            blogUserRepository.save(user);
            return user;
        }
        return null;
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

    /**
     * 将 BlogUser 转换为 UserVO
     *
     * @param user BlogUser
     * @return UserVO
     */
    private UserVO toUserVO(BlogUser user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setEmail(user.getEmail());
        userVO.setAvatar(user.getAvatar());
        userVO.setBio(user.getBio());
        userVO.setWebsite(user.getWebsite());
        userVO.setGithub(user.getGithub());
        userVO.setWeibo(user.getWeibo());
        return userVO;
    }
}