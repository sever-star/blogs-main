package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRevisionResponse {
    private Long id;
    private Integer versionNo;
    private Long postId;
    private String title;
    private String contentMd;
    private String contentHtml;
    private String summary;
    private String coverImage;
    private Long categoryId;
    private Integer editedBy;
    private String editedByName;
    private LocalDateTime editedAt;
    private String changeNote;
}
