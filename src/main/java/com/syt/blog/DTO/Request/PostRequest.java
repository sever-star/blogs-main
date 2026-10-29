package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @NotBlank
    @Size(min = 1, max = 200)
    private String title;
    @NotBlank
    @Size(min = 50)
    private String contentMd;

    private String summary;

    private String coverImage;
    @NotNull
    private Integer categoryId;

    // 发布状态：0=草稿 1=已发布 2=已下架（审核状态由 review_status 承载，不走本字段）
    // 新文章缺省应为草稿 0——历史默认 2 在旧语义里是「待审核」，新语义下会变成「已下架」
    @NotNull
    private Integer status = 0;
    private Integer userId;
    private String contentHtml;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favCount;
    // 计划发布时间（非空且 status=0 表示定时待发布；定时发布功能尚未实现，预留字段）
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @NotNull
    private List<Integer> tags;
}
