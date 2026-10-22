package com.syt.blog.controller;


import com.syt.blog.DTO.Request.ChangePasswordRequest;
import com.syt.blog.DTO.Response.LoginResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.DTO.Request.LoginRequest;
import com.syt.blog.DTO.Request.RegisterRequest;
import com.syt.blog.DTO.Request.UpdateProfileRequest;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.DTO.Mapper.UserMapper;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.DTO.Response.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
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
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        BlogUsers blogUsers = UserMapper.INSTANCE.login(loginRequest);
        LoginResponse loginResponse=blogUserService.login(blogUsers, request,response);
        return Result.success(loginResponse);
    }

    /**
     * 获取当前用户信息
     * <p>
     * 身份来自 SecurityContext，不再读 Authorization 头——该路径已声明 authenticated()，
     * 无 token 的请求在过滤器链里就被 EntryPoint 挡下了。
     */
    @GetMapping("/me")
    public Result<UserResponse> me() {
        UserResponse userResponse = blogUserService.getUser();
        return Result.success(userResponse);
    }



    /**
     * 更新用户信息
     */
    @PutMapping("/me")
    public Result<UserResponse> update(@RequestBody @Valid UpdateProfileRequest updateProfileRequest) {
        BlogUsers blogUsers = UserMapper.INSTANCE.updateProfile(updateProfileRequest);
        UserResponse updatedUser = blogUserService.update(blogUsers);
        return Result.success(updatedUser);
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        BlogUsers blogUsers = UserMapper.INSTANCE.register(registerRequest);
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

        try {
            // 必须用 refresh 类型的解析方法：否则一张 access token 也能换出新 token，
            // 等于把 15 分钟的凭证续期成 7 天（见 docs/security-migration.md §3）
            Integer userId = jwtUtils.getUserIdFromRefreshToken(refreshToken);
            String username = jwtUtils.getUsernameFromRefreshToken(refreshToken);

            refreshTokenService.revokeRefreshToken(refreshToken);
            Long tokenVersion=blogUserService.getTokenVersion(userId);
            String newAccessToken = jwtUtils.generateToken(userId, username,tokenVersion);
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
            // 必须抛异常，不能 return Result.error(...)：后者以 HTTP 200 返回，
            // 前端只能靠 body 判断成败，违背 docs/security-migration.md §9 的状态码约定。
            // 也不透传 e.getMessage()：JJWT 会抛 "CharSequence cannot be null or empty."
            // 这类实现细节，对用户没有意义，只在 debug 日志里留一份。
            log.debug("刷新令牌失败: {}", e.toString());
            if (e instanceof ExpiredJwtException) {
                throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token 已过期，请刷新");
            }
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效，请重新登录");
        }
    }
    /**
     * 更新密码
     *
     * */
    @PutMapping("/me/password")
    public Result updatePassword(@RequestBody @Valid ChangePasswordRequest changePasswordRequest) {
        blogUserService.updatePassword(changePasswordRequest);
        return Result.success("密码更新成功");
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