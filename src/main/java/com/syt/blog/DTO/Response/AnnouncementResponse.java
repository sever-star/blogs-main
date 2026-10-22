package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private Long id;
    private String title;
    private String content;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
