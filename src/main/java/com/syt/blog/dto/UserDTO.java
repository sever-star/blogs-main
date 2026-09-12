package com.syt.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {


    /** 昵称 */
    private String nickname;


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
}
