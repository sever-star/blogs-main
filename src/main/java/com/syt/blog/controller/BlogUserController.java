package com.syt.blog.controller;


import com.syt.blog.DTO.Response.LoginResponse;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.DTO.Request.UserRequest;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.DTO.Mapper.UserMapper;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.DTO.Response.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class BlogUserController {

    private final BlogUserService blogUserService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid UserRequest userRequest,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        BlogUsers blogUsers = UserMapper.INSTANCE.Login(userRequest);
        LoginResponse loginResponse=blogUserService.login(blogUsers, request,response);
        return Result.success(loginResponse);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserResponse> me(@RequestHeader("Authorization") String authorization) {
        UserResponse userResponse = blogUserService.getUser(authorization);
        return Result.success(userResponse);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/me")
        public Result<UserResponse> update(@RequestHeader("Authorization") String authorization,
                                           @RequestBody @Valid UserRequest userRequest) {
        BlogUsers blogUsers = UserMapper.INSTANCE.userDTOToBlogUsers(userRequest);
        UserResponse updatedUser = blogUserService.update(authorization, blogUsers);
        return Result.success(updatedUser);
    }
    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid UserRequest UserRequest) {
        BlogUsers blogUsers = UserMapper.INSTANCE.userDTOToBlogUsers(UserRequest);
        LoginResponse loginResponse = blogUserService.register(blogUsers);
        return Result.success(loginResponse);

    }
    /**
     * 刷新Token
     */

    @PostMapping("/refresh")
    public Result<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.error(ErrorCode.TOKEN_EXPIRED, "Refresh token 不存在，请重新登录");
        }

        try {
            //RefreshToken rt = refreshTokenService.verifyRefreshToken(refreshToken);
            Integer userId = jwtUtils.getUserIdFromToken(refreshToken);
            String username = jwtUtils.getUsernameFromToken(refreshToken);

            refreshTokenService.revokeRefreshToken(refreshToken);

            String newAccessToken = jwtUtils.generateToken(userId, username);
            String newRefreshToken = jwtUtils.generateRefreshToken(userId, username);

            String userAgent = request.getHeader("User-Agent");
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            refreshTokenService.saveRefreshToken(userId, newRefreshToken, null, userAgent, ip);

            setRefreshTokenCookie(response, newRefreshToken, refreshExpiration);

            return Result.success(new AccessTokenDTO(newAccessToken));
        } catch (RuntimeException e) {
            clearRefreshTokenCookie(response);
            return Result.error(ErrorCode.TOKEN_EXPIRED, e.getMessage());
        }
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                            HttpServletResponse response) {
        blogUserService.logout(refreshToken, response);
        return Result.success("退出登录成功");
    }

    /**
     * 设置刷新令牌Cookie
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
     * 清除刷新令牌Cookie
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
     * 访问令牌DTO
     */
    public record AccessTokenDTO(String accessToken) {}
}