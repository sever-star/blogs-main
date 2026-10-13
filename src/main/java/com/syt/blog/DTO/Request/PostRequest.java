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

    // readingTime / allowComment 为可选（API 文档契约），缺省由
    // BlogPostService 兜底——仅靠字段默认值不可靠：反序列化后可能仍是 null，
    // 而 @NotNull 会把「没传」误判成 400。
    private Integer readingTime=15;
    // 发布状态：0=草稿 1=已发布 2=已下架（审核状态由 review_status 承载，不走本字段）
    // 新文章缺省应为草稿 0——历史默认 2 在旧语义里是「待审核」，新语义下会变成「已下架」
    @NotNull
    private Integer status = 0;
    private Short allowComment=1;
    private Integer userId;
    private String contentHtml;
    private String tocHtml;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favCount;
    private Short isTop;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @NotNull
    private List<Integer> tags;
}
