package com.syt.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手（DeepSeek）配置。
 * <p>
 * 对应 application-dev.yaml 的 {@code ai.deepseek} 节点。API Key 通过环境变量
 * {@code DEEPSEEK_API_KEY} 注入，避免密钥随仓库提交；未配置时接口会返回
 * 「AI 服务未配置」的友好提示，而不是启动失败。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class AiProperties {

    /** 接口基础地址（OpenAI 兼容协议，默认 DeepSeek 官方） */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key，为空表示未配置 */
    private String apiKey = "";

    /** 模型名 */
    private String model = "deepseek-chat";

    /** 单次请求超时（秒）。流式生成耗时长，需大于模型最长生成时间 */
    private int timeoutSeconds = 120;

    /** 携带的历史消息条数上限（不含 system），控制上下文 token 成本 */
    private int maxContextMessages = 20;

    /** 单次回复最大 token 数 */
    private int maxTokens = 2048;

    /** 采样温度 */
    //模型生成文本时的随机性和创造性，接近0输出更稳定，解决1输出更随机
    private double temperature = 0.7;

    /** 游客每 IP 每小时提问次数上限 */
    private int guestHourlyLimit = 10;

    /** 单次提问附件文本总字符上限，超出部分截断 */
    private int maxAttachmentChars = 20000;
}
