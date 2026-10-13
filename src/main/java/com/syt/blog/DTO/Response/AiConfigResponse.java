package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 能力状态，供前端在未配置模型时给出提示，
 * 而不是等用户发出提问后才收到报错。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigResponse {

    /** 后端是否已配置 API Key */
    private boolean configured;

    /** 当前使用的模型名 */
    private String model;
}
