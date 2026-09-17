package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求 DTO
 * <p>
 * 只包含登录所需的用户名和密码，不包含任何多余字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min=3,max=20,message = "用户名长度在3到20个字符之间")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min=6,max=32,message = "密码长度在6到32个字符之间")
    private String password;
}
