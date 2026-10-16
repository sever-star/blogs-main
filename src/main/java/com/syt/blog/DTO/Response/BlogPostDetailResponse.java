package com.syt.blog.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlogPostDetailResponse extends PostResponse {
    private Boolean liked;        // 当前用户是否点赞
    private Boolean favorited;    // 当前用户是否收藏
}
