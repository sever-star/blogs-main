package com.syt.blog.Vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应 VO（View Object）
 * <p>
 * 用于登录成功等场景向前端返回用户信息，不包含密码等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

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

    /** JWT 令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;
}
