package com.syt.blog.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {
    private Integer id;
    private Integer userId;
    private String title;
    private Integer commentCount;
    private String contentMd;
    private String contentHtml;
    private String summary;
    private String tocHtml;
    private String coverImage;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favCount;
    private Short readingTime;
    /** 发布状态：0=草稿 1=已发布 2=已下架 */
    private Short status;
    /** 审核状态：0=待审核 1=通过 2=拒绝（仅「我的文章」等按需返回，为空时不序列化） */
    private Short reviewStatus;
    private Short isTop;
    private Short allowComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;


    private CategoryResponse category;
    private List<TagResponse> tags;

    private String authorNickname;
    private String authorAvatar;
}
