package com.syt.blog.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 工具类
 * <p>
 * 负责生成、解析和验证 JSON Web Token，
 * 用于用户登录后的身份认证。
 */
@Component
public class JwtUtils {

    /** 密钥（从配置文件读取，HS256 算法要求至少 32 字节） */
    @Value("${jwt.secret}")
    private String secret;

    /** Token 过期时间（毫秒） */
    @Value("${jwt.access-expiration}")
    private long expiration;
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /** 签名密钥对象 */
    private SecretKey key;

    /**
     * 初始化签名密钥
     */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== 生成 Token ====================

    /**
     * 根据用户信息生成 Token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateToken(Integer userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Integer userId,String username) {
        Date now = new Date();
        Date refreshExpiryDate = new Date(now.getTime() + refreshExpiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(refreshExpiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }
    // ==================== 解析 Token ====================

    /**
     * 解析 Token，获取声明信息
     *
     * @param token JWT 字符串
     * @return Claims 声明对象
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户 ID
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Integer getUserIdFromToken(String token) {
        return Integer.parseInt(parseToken(token).getSubject());
    }

    /**
     * 从 Token 中获取用户名
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    // ==================== 验证 Token ====================

    /**
     * 验证 Token 是否有效（签名正确且未过期）
     *
     * @param token JWT 字符串
     * @return true-有效，false-无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}