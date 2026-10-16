package com.syt.blog.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syt.blog.jooq.tables.records.BlogCommentsRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentsResponse {
    private Long id;
    private Integer articleId;
    private Integer userId;
    private Long parentId;
    private Long replyToId;
    private String nickname;
    private String avatar;
    private String content;
    private Integer likeCount;
    private short isAdmin;
    private LocalDateTime createdAt;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<CommentsResponse> replies;
    private boolean liked;

    public CommentsResponse(Integer likeCount, BlogCommentsRecord comment) {
        this.articleId = comment.getPostId();
        this.userId = comment.getUserId();
        this.parentId = comment.getParentId();
        this.replyToId = comment.getReplyToId();
        this.nickname = comment.getNickname();
        this.avatar = comment.getAvatar();
        this.content = comment.getContent();
        this.isAdmin = comment.getIsAdmin();
        this.createdAt = comment.getCreatedAt();
        this.likeCount = likeCount;
    }
}