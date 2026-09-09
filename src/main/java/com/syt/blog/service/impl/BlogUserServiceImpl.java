package com.syt.blog.service.impl;

import com.syt.blog.dto.LoginDTO;
import com.syt.blog.entity.BlogUser;
import com.syt.blog.repository.BlogUserRepository;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.Vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class BlogUserServiceImpl implements BlogUserService {

    private final BlogUserRepository blogUserRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @param request HTTP 请求
     * @return 登录结果
     */
    @Override
    public UserVO login(LoginDTO loginDTO, HttpServletRequest request) {
        BlogUser user = blogUserRepository.findByUsername(loginDTO.getUsername());
        if (user == null || !user.getPassword().equals(loginDTO.getPassword())) {
            return null;
        }
        String accessToken = jwtUtils.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIp(request);
        refreshTokenService.saveRefreshToken(
                user.getId(), refreshToken, null, userAgent, ip
        );

        UserVO userVO = toUserVO(user);
        userVO.setAccessToken(accessToken);
        userVO.setRefreshToken(refreshToken);
        return userVO;
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
    @Override
    public UserVO getUser(Integer id) {
        return toUserVO(blogUserRepository.findById(id).orElse(null));
    }

    /**
     * 用户注册
     *
     * @param blogUser 用户信息
     * @return 注册结果
     */
    @Override
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
    @Override
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
        return userVO;
    }
}