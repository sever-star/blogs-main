package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 对话请求体。
 * <p>
 * 前端把文本类附件（txt/md/csv/json/代码等）在浏览器里读成纯文本后放进
 * {@link Attachment#getContent()}，随本轮提问一起送给模型；图片等二进制附件
 * 先传到 OSS，只带 {@link Attachment#getUrl()}，模型侧仅记录来源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /** 会话 id；为空表示本轮新建会话 */
    private Long sessionId;

    /** 提问正文；允许为空（此时视为「请阅读附件」） */
    @Size(max = 8000, message = "提问内容过长，请精简后再发送")
    private String content;

    /** 附件列表，可为空 */
    @Size(max = 5, message = "单次最多上传 5 个附件")
    private List<Attachment> attachments;

    /** 单个附件 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {

        /** 原始文件名，用于在提示里标注来源 */
        private String name;

        /** 文本内容（文本类附件），二进制附件为空 */
        @Size(max = 200000, message = "附件内容过大")
        private String content;

        /** 二进制附件的公开访问地址（OSS），可为空 */
        private String url;
    }
}
