package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** AI 消息明细 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageResponse {

    private Long id;

    /** 角色：user 用户提问 / assistant AI 回复 */
    private String role;

    private String content;

    /** 消耗 token 数（仅 assistant 消息有值） */
    private Integer tokens;

    /** 状态：0 生成中 / 1 已完成 / 2 失败 */
    private Short status;

    private LocalDateTime createdAt;
}
