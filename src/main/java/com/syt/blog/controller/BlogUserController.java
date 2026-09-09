package com.syt.blog.controller;


import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.dto.LoginDTO;
import com.syt.blog.entity.BlogUser;
import com.syt.blog.entity.RefreshToken;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.Vo.UserVO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody LoginDTO loginDTO,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        UserVO userVO = blogUserService.login(loginDTO, request);
        if (userVO != null) {
            setRefreshTokenCookie(response, userVO.getRefreshToken(), refreshExpiration);
            userVO.setRefreshToken(null);
            return Result.success(userVO);
        }
        return Result.error(ErrorCode.AUTH_FAILED, "用户名或密码错误");
    }

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        Integer userId = jwtUtils.getUserIdFromToken(token);
        UserVO userVO = blogUserService.getUser(userId);
        if (userVO != null) {
            return Result.success(userVO);
        }
        return Result.error(ErrorCode.AUTH_FAILED, "用户不存在");
    }

    @PutMapping("/me")
    public Result<BlogUser> update(@RequestHeader("Authorization") String authorization,
                                   @RequestBody BlogUser blogUser) {
        String token = authorization.replace("Bearer ", "");
        BlogUser updatedUser = blogUserService.update(token, blogUser);
        if (updatedUser != null)
            return Result.success(updatedUser);
        return Result.error(ErrorCode.AUTH_FAILED,"更新失败");
    }
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody BlogUser blogUser) {
        UserVO userVO = blogUserService.register(blogUser);
        if (userVO != null) {
            return Result.success(userVO);
        }
        return Result.error(ErrorCode.AUTH_FAILED, "注册失败");
    }

    @PostMapping("/refresh")
    public Result<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.error(ErrorCode.TOKEN_EXPIRED, "Refresh token 不存在，请重新登录");
        }

        try {
            RefreshToken rt = refreshTokenService.verifyRefreshToken(refreshToken);
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

    @PostMapping("/logout")
    public Result<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
        clearRefreshTokenCookie(response);
        return Result.success("退出登录成功");
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMs) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (maxAgeMs / 1000));
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public record AccessTokenDTO(String accessToken) {}
}