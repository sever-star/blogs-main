package com.syt.blog.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
    public String generateToken(Integer userId, String username,long tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .claim("tv",tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Integer userId,String username, long tokenVersion) {
        Date now = new Date();
        Date refreshExpiryDate = new Date(now.getTime() + refreshExpiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "refresh")
                .claim("tv",tokenVersion)
                .issuedAt(now)
                .expiration(refreshExpiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }
    // ==================== 解析 Token ====================

    /** access token 的 type 声明值 */
    private static final String TYPE_ACCESS = "access";
    /** refresh token 的 type 声明值 */
    private static final String TYPE_REFRESH = "refresh";

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
     * 解析并校验 token 类型。
     * <p>
     * access 与 refresh 两种 token 结构完全相同、同一密钥签名，唯一区别就是 {@code type} claim。
     * 不校验的话：refresh token 能当 Bearer 调业务接口（7 天有效期绕过 15 分钟设计），
     * access token 也能拿到刷新端点。类型校验必须绑在解析这一步，
     * 而不是靠调用方「记得去查」——后者一定会漏。
     *
     * @throws io.jsonwebtoken.JwtException 签名无效、已过期、或类型不匹配
     */
    private Claims parseTokenWithExpectedType(String token, String expectedType) {
        Claims claims = parseToken(token);
        String actual = claims.get("type", String.class);
        if (!expectedType.equals(actual)) {
            throw new MalformedJwtException(
                    "Token 类型不匹配，期望 [" + expectedType + "]，实际 [" + actual + "]");
        }
        return claims;
    }

    /**
     * 解析 **access** token 取用户 ID。调用方：JWT 认证过滤器。
     *
     * @throws io.jsonwebtoken.JwtException token 无效、过期，或实际是 refresh token
     */
    public Integer getUserIdFromAccessToken(String token) {
        return Integer.parseInt(parseTokenWithExpectedType(token, TYPE_ACCESS).getSubject());
    }

    /**
     * 解析 **access** token 取用户名。
     *
     * @throws io.jsonwebtoken.JwtException token 无效、过期，或实际是 refresh token
     */
    public String getUsernameFromAccessToken(String token) {
        return parseTokenWithExpectedType(token, TYPE_ACCESS).get("username", String.class);
    }
    public long getTokenVersion(String token) {
        Claims claims= parseTokenWithExpectedType(token, TYPE_ACCESS);
        return claims.get("tv",Long.class)==null?0:claims.get("tv",Long.class);
    }

    /**
     * 解析 **refresh** token 取用户 ID。调用方：刷新端点（refresh token 走 Cookie，不走 Header）。
     *
     * @throws io.jsonwebtoken.JwtException token 无效、过期，或实际是 access token
     */
    public Integer getUserIdFromRefreshToken(String token) {
        return Integer.parseInt(parseTokenWithExpectedType(token, TYPE_REFRESH).getSubject());
    }

    /**
     * 解析 **refresh** token 取用户名。
     *
     * @throws io.jsonwebtoken.JwtException token 无效、过期，或实际是 access token
     */
    public String getUsernameFromRefreshToken(String token) {
        return parseTokenWithExpectedType(token, TYPE_REFRESH).get("username", String.class);
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