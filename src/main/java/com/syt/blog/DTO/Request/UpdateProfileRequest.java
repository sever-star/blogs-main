package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新个人资料请求
 * <p>
 * 只包含「本人可以自由修改」的展示类字段，全部可选（PATCH 语义：传了才改）。
 * <p>
 * 特别注意这里**故意不包含**：
 * <ul>
 *   <li>username / email —— 账号标识，不允许通过此接口修改；</li>
 *   <li>password —— 改密码要验证旧密码，走独立的接口；</li>
 *   <li>status / role / 各类时间戳 —— 服务端与数据库维护。</li>
 * </ul>
 * 这些字段不出现在 DTO 里，客户端就没有传的入口，
 * 「不可改」由类型保证，而不是靠 MapStruct 的 ignore 注解去记得忽略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /**
     * 头像URL
     */
    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    /**
     * 个人简介
     */
    @Size(max = 200, message = "个人简介长度不能超过200个字符")
    private String bio;

    /**
     * 个人网站
     */
    @Size(max = 255, message = "个人网站长度不能超过255个字符")
    private String website;

    /**
     * GitHub地址
     */
    @Size(max = 255, message = "GitHub地址长度不能超过255个字符")
    private String github;

    /**
     * 微博地址
     */
    @Size(max = 255, message = "微博地址长度不能超过255个字符")
    private String weibo;
}
