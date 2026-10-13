package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 新建 AI 会话请求体 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionRequest {

    /** 会话标题；为空时后端用「新的对话」，收到首条提问后自动命名 */
    @Size(max = 150, message = "会话标题过长")
    private String title;
}
