package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求
 * <p>
 * 只收用户名与密码：登录不需要、也不应该接收邮箱、昵称等资料字段。
 * 与 {@link RegisterRequest}、{@link UpdateProfileRequest} 分开，
 * 是为了让每个接口的校验规则互不牵连。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（明文，服务端用 BCrypt 比对）
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
