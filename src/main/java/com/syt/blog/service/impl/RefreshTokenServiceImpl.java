package com.syt.blog.service.impl;

import com.syt.blog.entity.RefreshToken;
import com.syt.blog.repository.RefreshTokenRepository;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;

    @Override
    public RefreshToken saveRefreshToken(Integer userId, String token, String deviceId, String userAgent, String ip) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .token(token)
                .deviceId(deviceId)
                .userAgent(userAgent)
                .ip(ip)
                .expiresAt(new Date(System.currentTimeMillis() + jwtUtils.getRefreshExpiration()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token 不存在"));

        if (refreshToken.getRevoked() != null && refreshToken.getRevoked()) {
            throw new RuntimeException("Refresh token 已被撤销");
        }

        if (refreshToken.getExpiresAt().before(new Date())) {
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
    @Override
    public void revokeRefreshToken(String token) {
        //使用ifPresent判断长有效token是否存在。存在撤销
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setUpdatedAt(new Date());
            refreshTokenRepository.save(rt);
        });
    }
}