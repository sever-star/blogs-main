package com.syt.blog.Vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    /** JWT 令牌 */
    private String accessToken;

    private UserVO user;
}
