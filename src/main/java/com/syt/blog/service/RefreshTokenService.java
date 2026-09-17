package com.syt.blog.service;


import com.syt.blog.jooq.tables.daos.BlogRefreshTokensDao;
import com.syt.blog.jooq.tables.pojos.BlogRefreshTokens;
import com.syt.blog.repository.RefreshTokenRepository;
import com.syt.blog.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final BlogRefreshTokensDao refreshTokensDao;
    private final JwtUtils jwtUtils;
    public BlogRefreshTokens saveRefreshToken(Integer userId, String token, String deviceId, String userAgent, String ip) {
        BlogRefreshTokens refreshToken = new BlogRefreshTokens();
        refreshToken.setUserId(userId);
        refreshToken.setToken(token);
        refreshToken.setDeviceId(deviceId);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setIp(ip);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpiration()));
        refreshToken.setRevoked(0);
        refreshTokensDao.insert(refreshToken);
        return refreshToken;
    }

    public BlogRefreshTokens verifyRefreshToken(String token) {
        BlogRefreshTokens refreshToken = refreshTokenRepository.findByToken(token);
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token 不存在");
        }

        if (refreshToken.getRevoked() != null && refreshToken.getRevoked().equals(1)) {
            throw new RuntimeException("Refresh token 已被撤销");
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token 已过期");
        }

        if (!jwtUtils.validateToken(token)) {
            throw new RuntimeException("Refresh token 签名无效");
        }

        return refreshToken;
    }
    /**
     * 撤销刷新令牌
     *
     * @param token 刷新令牌
     */
    public void revokeRefreshToken(String token) {
        BlogRefreshTokens refreshToken = refreshTokenRepository.findByToken(token);
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token 不存在");
        }
        refreshToken.setRevoked(1);
        refreshToken.setUpdatedAt(LocalDateTime.now());
        refreshTokensDao.update(refreshToken);
    }
}