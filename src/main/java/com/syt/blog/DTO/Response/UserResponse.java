package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息响应 VO（View Object）
 * <p>
 * 用于登录成功等场景向前端返回用户信息，不包含密码等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** 用户 ID */
    private Integer id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 简介 */
    private String bio;
    /** 个人网站 */
    private String website;

    /** github地址 */
    private String github;

    /**微博地址*/
    private String weibo;

    /**账户状态*/
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
