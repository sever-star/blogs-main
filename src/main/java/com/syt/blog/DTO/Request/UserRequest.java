package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min=3,max=20,message = "用户名长度在3到20个字符之间")
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 密码(加密存储)
     */
    @NotBlank(message = "密码不能为空")
    @Size(min=6,max=32,message = "密码长度在6到32个字符之间")
    private String password;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 状态
     */
    private Short status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 个人网站
     */
    private String website;

    /**
     * GitHub地址
     */
    private String github;

    /**
     * 微博地址
     */
    private String weibo;

    /**
     * 注册时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
