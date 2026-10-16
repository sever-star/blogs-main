package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentRequest {
    @NotBlank
    @Size(min=1,max=1000,message="评论内容长度必须在1-1000之间")
    private String content;
    private Long parentId;
    private Long replyToId;
}
