package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文章标题/摘要生成请求体。
 * <p>
 * 标题生成只用 {@code contentMd}；摘要生成可带 {@code title} 作为上下文（可空）。
 * 内容过短时模型难以生成有意义的结果，但仍交给模型处理，不做长度强校验。
 */
@Data
public class AiGenerateRequest {
    /** 文章正文（Markdown），生成标题/摘要的依据 */
    @NotBlank
    private String contentMd;

    /** 文章标题，仅生成摘要时作为上下文使用，可空 */
    private String title;
}
