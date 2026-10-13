package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求
 * <p>
 * 用户名、密码、邮箱在 blog_users 中均为 NOT NULL，因此三者必填；
 * 昵称/头像/简介可选。status、role、各类时间戳一律不接收——
 * 它们由数据库默认值或服务层维护，避免客户端自行指定权限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度在3到20个字符之间")
    private String username;

    /**
     * 密码（明文，入库前 BCrypt 加密）
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度在6到32个字符之间")
    private String password;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 昵称（可选，不填则前端可回退显示用户名）
     */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /**
     * 头像URL（可选）
     */
    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    /**
     * 个人简介（可选）
     */
    @Size(max = 200, message = "个人简介长度不能超过200个字符")
    private String bio;
}
