package com.syt.blog.controller;


import com.syt.blog.Vo.LoginResponse;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.dto.LoginDTO;
import com.syt.blog.dto.RegisterDTO;
import com.syt.blog.dto.UserDTO;
import com.syt.blog.entity.BlogUser;
import com.syt.blog.entity.RefreshToken;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.Vo.UserVO;
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginDTO loginDTO,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        LoginResponse loginResponse=blogUserService.login(loginDTO, request,response);
        return Result.success(loginResponse);
    }

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader("Authorization") String authorization) {
        UserVO userVO = blogUserService.getUser(authorization);
        return Result.success(userVO);
    }

    @PutMapping("/me")
        public Result<UserVO> update(@RequestHeader("Authorization") String authorization,
                                   @RequestBody UserDTO userDTO) {
        UserVO updatedUser = blogUserService.update(authorization, userDTO);
        return Result.success(updatedUser);
    }
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterDTO registerDTO) {
        LoginResponse loginResponse = blogUserService.register(registerDTO);
        return Result.success(loginResponse);

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
        blogUserService.logout(refreshToken, response);
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