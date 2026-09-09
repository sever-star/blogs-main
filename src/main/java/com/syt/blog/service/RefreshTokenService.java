package com.syt.blog.service;

import com.syt.blog.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken saveRefreshToken(Integer userId, String token, String deviceId, String userAgent, String ip);

    RefreshToken verifyRefreshToken(String token);

    void revokeRefreshToken(String token);
}