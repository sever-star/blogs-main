package com.syt.blog.service;

import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大模型客户端（OpenAI 兼容协议，当前对接 DeepSeek）。
 * <p>
 * 系统不从前端直连大模型 —— 否则 API Key 会暴露在浏览器里、计费也无法控制。
 * 这里作为唯一调用方，负责组装 messages、发起 {@code stream: true} 请求并解析
 * 上游的 SSE 分片，逐个增量回调给上层。
 * <p>
 * 用 JDK 自带的 {@link HttpClient} 而非 WebFlux：流式对话是「一次请求、长连接读流」，
 * {@code BodyHandlers.ofInputStream()} 已足够，无需为 SSE 引入整套响应式依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    /** 上游 SSE 的数据行前缀 */
    private static final String DATA_PREFIX = "data:";
    /** 上游流结束标记 */
    private static final String DONE_FLAG = "[DONE]";
    /** 错误响应体截断长度，避免把整页 HTML 错误页塞进提示信息 */
    private static final int ERROR_BODY_LIMIT = 500;

    private final AiProperties properties;
    private final JsonMapper jsonMapper;

    /** 复用同一个 HttpClient：内部持有连接池，每次新建会浪费连接与线程 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 一条对话消息。
     *
     * @param role    角色：system / user / assistant
     * @param content 文本内容
     */
    public record ChatMessage(String role, String content) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /** 是否已配置 API Key */
    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    /**
     * 流式对话：每收到一个增量片段即回调 {@code onDelta}，方法阻塞至生成结束。
     *
     * @param messages 完整上下文（含 system）
     * @param onDelta  增量回调，在调用线程上串行执行
     * @return 本次消耗的总 token 数；上游未返回 usage 时为 0
     * @throws BusinessException 未配置 Key、上游报错、超时或流中断
     */
    public int streamChat(List<ChatMessage> messages, Consumer<String> onDelta) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务未配置：请设置环境变量 DEEPSEEK_API_KEY，或填写 ai.deepseek.api-key");
        }

        HttpResponse<InputStream> response = send(messages);
        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务返回 " + response.statusCode() + "：" + readErrorBody(response.body()));
        }
        return consumeStream(response.body(), onDelta);
    }

    /** 非流式补全结果：模型返回的完整文本 + 本次消耗 token */
    public record Completion(String content, int tokens) {
    }

    /**
     * 非流式对话（一次性返回完整文本）。用于文章标题/摘要生成等不需要增量输出的场景。
     * <p>
     * 与 {@link #streamChat} 共用 {@code send} 的鉴权/超时/Key 检查，但请求体
     * {@code stream=false}、Accept {@code application/json}，直接读完整响应体解析
     * {@code choices[0].message.content}。失败语义与 {@link #streamChat} 一致。
     */
    public Completion complete(List<ChatMessage> messages) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务未配置：请设置环境变量 DEEPSEEK_API_KEY，或填写 ai.deepseek.api-key");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("stream", false);
        payload.put("temperature", properties.getTemperature());
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());

        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 请求被中断");
        } catch (IOException e) {
            log.warn("调用 AI 服务失败", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "连接 AI 服务失败：" + e.getMessage());
        }

        if (response.statusCode() != 200) {
            String body = response.body();
            String snippet = body == null ? "无响应内容"
                    : body.substring(0, Math.min(body.length(), ERROR_BODY_LIMIT));
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务返回 " + response.statusCode() + "：" + snippet);
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(response.body());
        } catch (Exception e) {
            // Jackson 3 的解析异常为非受检；统一转业务异常，避免把堆栈抛给前端
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 响应解析失败：" + e.getMessage());
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务异常：" + error.path("message").asString("未知错误"));
        }
        String content = root.path("choices").path(0).path("message").path("content").asString("");
        int tokens = root.path("usage").path("total_tokens").asInt(0);
        return new Completion(content, tokens);
    }

    /** 组装并发送 chat/completions 请求，返回未读取的响应流 */
    private HttpResponse<InputStream> send(List<ChatMessage> messages) {
        //设置给大模型数据的map集合
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("stream", true);//开启流式输出
        payload.put("temperature", properties.getTemperature());
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        // 让上游在最后一个分片里带上 usage，用于统计 token 消耗
        payload.put("stream_options", Map.of("include_usage", true));

        //设置发送请求
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))//设置超时时间
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        try {
            //同步发送请求，阻塞当前线程直到响应头到达，参数二设置不把响应体一次性读完，返回inputStream
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            // 恢复中断标记后转业务异常，避免中断信号被吞掉
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 请求被中断");
        } catch (IOException e) {
            log.warn("调用 AI 服务失败", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "连接 AI 服务失败：" + e.getMessage());
        }
    }

    /**
     * 解析上游 SSE 流：逐行取 {@code data: {...}}，抽取
     * {@code choices[0].delta.content} 回调出去，并记录末尾 usage。
     * <p>
     * 上游在流中途报错时（如触发限流）会在 data 里带 error 字段，此处一并转成业务异常。
     */
    private int consumeStream(InputStream body, Consumer<String> onDelta) {
        int totalTokens = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过空行、注释行与 event:/id: 等非数据字段
                if (!line.startsWith(DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(DATA_PREFIX.length()).trim();
                if (data.isEmpty() || DONE_FLAG.equals(data)) {
                    continue;
                }

                JsonNode node = jsonMapper.readTree(data);

                JsonNode error = node.path("error");
                if (!error.isMissingNode() && !error.isNull()) {
                    throw new BusinessException(ErrorCode.AI_UNAVAILABLE,
                            "AI 服务异常：" + error.path("message").asString("未知错误"));
                }

                JsonNode content = node.path("choices").path(0).path("delta").path("content");
                if (content.isString() && !content.asString().isEmpty()) {
                    onDelta.accept(content.asString());
                }

                JsonNode usage = node.path("usage");
                if (usage.isObject()) {
                    totalTokens = usage.path("total_tokens").asInt(totalTokens);
                }
            }
        } catch (IOException e) {
            // 读流中断：已生成的内容由上层落库保留，此处仅标记失败
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 响应流中断：" + e.getMessage());
        }
        return totalTokens;
    }

    /** 读取错误响应体（截断），用于拼装可读的失败提示 */
    private String readErrorBody(InputStream body) {
        try (InputStream in = body) {
            String text = new String(in.readNBytes(ERROR_BODY_LIMIT), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? "无响应内容" : text;
        } catch (IOException e) {
            return "读取错误响应失败";
        }
    }
}
