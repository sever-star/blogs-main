package com.syt.blog.dto;

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
public class LoginDTO {

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;
}
