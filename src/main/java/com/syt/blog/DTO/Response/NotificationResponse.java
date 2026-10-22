package com.syt.blog.DTO.Response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知响应。
 * content 由后端在生成通知时拼好（含触发者昵称），前端直接展示，无需再解析关联对象。
 * link 指向来源：文章详情 /article/{id}，评论类带锚点 /article/{id}#comment-{commentId}。
 */
@Data
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String content;
    private String link;
    private Short isRead;
    private LocalDateTime createdAt;
}
