package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** AI 会话（对话列表项） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionResponse {

    private Long id;

    /** 会话标题，默认取首条提问的前若干字 */
    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
