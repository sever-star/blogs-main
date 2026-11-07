package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户主页统计 VO：文章数 / 获赞数 / 粉丝数。
 * <p>
 * 三个值均为对外可见的公开计数：
 * <ul>
 *   <li>articleCount：该用户已发布（status=1）且未软删的文章数</li>
 *   <li>likeCount：该用户文章收到的点赞总数（SUM(blog_posts.like_count)，已发布文章）</li>
 *   <li>followerCount：关注该用户的粉丝数（blog_follows.followee_id 计数）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {
    private Integer articleCount;
    private Integer likeCount;
    private Integer viewCount;
    private Integer followerCount;
}
