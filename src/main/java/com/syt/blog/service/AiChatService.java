package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.AiMapper;
import com.syt.blog.DTO.Request.AiChatRequest;
import com.syt.blog.DTO.Response.AiConfigResponse;
import com.syt.blog.DTO.Response.AiMessageResponse;
import com.syt.blog.DTO.Response.AiSessionResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.config.AiProperties;
import com.syt.blog.jooq.tables.pojos.BlogAiMessages;
import com.syt.blog.jooq.tables.pojos.BlogAiSessions;
import com.syt.blog.repository.AiRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.syt.blog.jooq.tables.BlogAiSessions.BLOG_AI_SESSIONS;

/**
 * AI 助手业务逻辑：会话管理 + 流式对话编排。
 * <p>
 * 一次提问的完整链路：校验额度 → 定位/新建会话 → 落库用户消息 →
 * 组装上下文 → 预插「生成中」的助手消息 → 调模型流式生成并逐段推给前端 →
 * 回填助手消息正文与 token 数。
 * <p>
 * 助手消息先落库再生成，是为了让「中途断流」也有痕迹：此时该条记录状态为失败，
 * 内容保留已生成的部分，前端据此提示重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 系统提示词：约束 AI 的角色、语言与输出格式 */
    private static final String SYSTEM_PROMPT = """
            你是「我的博客」站点内置的 AI 助手，主要帮助读者理解站内文章、解答技术问题，也协助博主进行写作。
            请遵守以下要求：
            1. 用简体中文回答，语气友好专业，不说空话套话；
            2. 涉及代码时给出可直接运行的示例，并用 Markdown 代码块标注语言；
            3. 长内容用标题或列表组织，善用 Markdown 排版；
            4. 不确定的内容要明确说明，不要编造事实、链接或引用；
            5. 若用户提供了附件，优先依据附件内容作答，并指明依据的是哪个文件。
            """;

    /** 会话标题最大长度（表字段 varchar(150)，这里取更适合列表展示的长度） */
    private static final int TITLE_MAX_LENGTH = 30;

    /** 会话列表返回条数上限 */
    private static final int SESSION_LIST_LIMIT = 100;

    /** 消息角色 */
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final AiRepository aiRepository;
    private final LlmClient llmClient;
    private final AiRateLimiter rateLimiter;
    private final AiProperties properties;

    /**
     * 流式生成是「阻塞读上游流」，用虚拟线程承载：每个请求一个线程，
     * 成本极低且不占用 Tomcat 的请求线程池。
     */
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdown();
    }

    // ==================== 会话归属 ====================

    /**
     * 会话归属者。
     *
     * @param userId 登录用户 id；游客为 null
     * @param ip     客户端 IP，游客会话按此归属
     */
    public record Owner(Integer userId, String ip) {
    }

    /**
     * 会话可见范围：登录用户看自己的，游客看本 IP 下未绑定用户的。
     * <p>
     * 两者取并集，这样「游客先聊、随后登录」也能接着原会话继续，而不会丢历史。
     */
    private Condition ownerCondition(Owner owner) {
        Condition byUser = owner.userId() != null
                ? BLOG_AI_SESSIONS.USER_ID.eq(owner.userId())
                : DSL.falseCondition();
        Condition byIp = BLOG_AI_SESSIONS.USER_ID.isNull()
                .and(BLOG_AI_SESSIONS.USER_IP.eq(owner.ip()));
        return byUser.or(byIp);
    }

    // ==================== 查询类接口 ====================

    /** AI 能力状态，供前端提示「未配置模型」 */
    public AiConfigResponse getConfig() {
        return new AiConfigResponse(llmClient.isConfigured(), properties.getModel());
    }

    /** 会话列表（最近活跃在前） */
    public List<AiSessionResponse> listSessions(Owner owner) {
        return aiRepository.listSessions(ownerCondition(owner), SESSION_LIST_LIMIT).stream()
                .map(AiMapper.INSTANCE::blogAiSessionsToAiSessionResponse)
                .toList();
    }

    /** 新建空会话 */
    public AiSessionResponse createSession(Owner owner, String title) {
        String normalized = normalizeTitle(title);
        long id = aiRepository.createSession(owner.userId(), owner.ip(), normalized);
        BlogAiSessions session = aiRepository.findSession(id);
        return AiMapper.INSTANCE.blogAiSessionsToAiSessionResponse(session);
    }

    /** 会话历史消息（时间正序） */
    public List<AiMessageResponse> listMessages(Owner owner, long sessionId) {
        requireOwnedSession(owner, sessionId);
        return aiRepository.listMessages(sessionId).stream()
                .map(AiMapper.INSTANCE::blogAiMessagesToAiMessageResponse)
                .toList();
    }

    /** 删除会话（消息随之级联删除） */
    public void deleteSession(Owner owner, long sessionId) {
        requireOwnedSession(owner, sessionId);
        aiRepository.deleteSession(sessionId);
    }

    // ==================== 流式对话 ====================

    /**
     * 发起一次流式对话，返回 SSE 发射器。
     * <p>
     * 方法本身立即返回，生成过程在虚拟线程中进行；前端按事件名分别处理：
     * {@code meta}（会话/消息 id）→ {@code content}（增量文本，多次）→
     * {@code done}（token 数）或 {@code error}（失败原因）。
     */
    public SseEmitter streamChat(Owner owner, AiChatRequest request) {
        if (owner.userId() == null) {
            rateLimiter.acquire(owner.ip());
        }

        String rawContent = request.getContent() == null ? "" : request.getContent().trim();
        List<AiChatRequest.Attachment> attachments = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();
        if (rawContent.isEmpty() && attachments.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_BAD_REQUEST, "提问内容不能为空");
        }

        long sessionId = resolveSession(owner, request.getSessionId(), rawContent, attachments);
        String question = buildQuestion(rawContent, attachments);

        // 用户消息状态直接记完成：它不会失败，失败的是后面的助手回复
        aiRepository.insertMessage(sessionId, ROLE_USER, question, AiRepository.STATUS_DONE);
        aiRepository.touchSession(sessionId);

        List<LlmClient.ChatMessage> context = buildContext(sessionId);
        // 预插助手消息占位，拿到 id 以便流式结束后回填
        long assistantMessageId = aiRepository.insertMessage(
                sessionId, ROLE_ASSISTANT, "", AiRepository.STATUS_GENERATING);

        SseEmitter emitter = new SseEmitter(Duration.ofSeconds(properties.getTimeoutSeconds() + 30).toMillis());
        // 首帧先告知 id：即使生成中途失败，前端也能把这条消息挂到正确位置并支持重试
        send(emitter, "meta", Map.of("sessionId", sessionId, "messageId", assistantMessageId));

        streamExecutor.execute(() -> runStream(emitter, assistantMessageId, context));
        return emitter;
    }

    /** 在虚拟线程中执行生成，把增量推给前端并回填落库 */
    private void runStream(SseEmitter emitter, long messageId, List<LlmClient.ChatMessage> context) {
        StringBuilder generated = new StringBuilder();
        try {
            int tokens = llmClient.streamChat(context, delta -> {
                generated.append(delta);
                send(emitter, "content", Map.of("delta", delta));
            });
            aiRepository.finishMessage(messageId, generated.toString(), tokens, AiRepository.STATUS_DONE);
            send(emitter, "done", Map.of("tokens", tokens));
        } catch (ClientGoneException e) {
            // 前端已断开：停止生成（异常已中断上游读流），保留已生成的部分
            log.info("客户端断开，提前结束生成, messageId={}, 已生成 {} 字", messageId, generated.length());
            aiRepository.finishMessage(messageId, generated.toString(), 0, AiRepository.STATUS_FAILED);
        } catch (Exception e) {
            log.warn("AI 流式生成失败, messageId={}", messageId, e);
            aiRepository.finishMessage(messageId, generated.toString(), 0, AiRepository.STATUS_FAILED);
            sendQuietly(emitter, "error", Map.of("message", friendlyMessage(e)));
        } finally {
            // 已经发过 error 事件，这里正常 complete 而不是 completeWithError，
            // 避免容器再向已消费的响应流追加一段错误页
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 客户端断开时容器可能已结束该 emitter，忽略即可
            }
        }
    }

    /** 组装完整上下文：system 提示词 + 最近 N 条已完成消息（含刚插入的本轮提问） */
    private List<LlmClient.ChatMessage> buildContext(long sessionId) {
        List<LlmClient.ChatMessage> messages = new ArrayList<>();
        messages.add(LlmClient.ChatMessage.system(SYSTEM_PROMPT));
        for (BlogAiMessages message : aiRepository.listRecentContext(sessionId, properties.getMaxContextMessages())) {
            messages.add(new LlmClient.ChatMessage(message.getRole(), message.getContent()));
        }
        return messages;
    }

    /**
     * 定位会话：传了 id 就复用（并校验归属），否则新建。
     * <p>
     * 空会话在收到第一条提问时按提问内容自动命名，省去用户手动改名。
     */
    private long resolveSession(Owner owner, Long sessionId, String rawContent,
                                List<AiChatRequest.Attachment> attachments) {
        if (sessionId == null) {
            return aiRepository.createSession(owner.userId(), owner.ip(),
                    buildTitle(rawContent, attachments));
        }

        requireOwnedSession(owner, sessionId);
        if (aiRepository.countMessages(sessionId) == 0) {
            aiRepository.renameSession(sessionId, buildTitle(rawContent, attachments));
        }
        return sessionId;
    }

    /** 用首条提问生成会话标题；提问为空时退化为附件名 */
    private String buildTitle(String rawContent, List<AiChatRequest.Attachment> attachments) {
        if (!rawContent.isEmpty()) {
            return normalizeTitle(rawContent);
        }
        return attachments.stream()
                .map(AiChatRequest.Attachment::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .map(this::normalizeTitle)
                .orElse("新的对话");
    }

    /** 压缩空白并截断，得到适合列表展示的标题 */
    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新的对话";
        }
        String normalized = title.replaceAll("\\s+", " ").trim();
        return normalized.length() > TITLE_MAX_LENGTH
                ? normalized.substring(0, TITLE_MAX_LENGTH) + "…"
                : normalized;
    }

    /**
     * 把提问正文与附件拼成最终送给模型的内容。
     * <p>
     * 文本类附件以代码块包裹并标注文件名，模型才能区分「哪些是用户的话、
     * 哪些是文件里的内容」；二进制附件只给地址，并明确说明读不到内容，
     * 避免模型对着 URL 编造文件内容。
     */
    private String buildQuestion(String rawContent, List<AiChatRequest.Attachment> attachments) {
        String content = rawContent;
        if (content.isEmpty()) {
            content = "请阅读我上传的附件并总结要点。";
        }
        if (attachments.isEmpty()) {
            return content;
        }

        StringBuilder builder = new StringBuilder(content);
        int budget = properties.getMaxAttachmentChars();
        for (AiChatRequest.Attachment attachment : attachments) {
            String name = (attachment.getName() == null || attachment.getName().isBlank())
                    ? "未命名文件"
                    : attachment.getName();
            String text = attachment.getContent();

            if (text != null && !text.isBlank()) {
                builder.append("\n\n【附件：").append(name).append("】\n```\n");
                if (budget <= 0) {
                    builder.append("（附件内容过长，已省略）");
                } else {
                    boolean truncated = text.length() > budget;
                    builder.append(truncated ? text.substring(0, budget) : text);
                    if (truncated) {
                        builder.append("\n……（内容过长，已截断）");
                    }
                    budget -= Math.min(text.length(), budget);
                }
                builder.append("\n```");
            } else if (attachment.getUrl() != null && !attachment.getUrl().isBlank()) {
                builder.append("\n\n【附件：").append(name).append("】\n（二进制文件，地址：")
                        .append(attachment.getUrl()).append("，无法直接读取内容）");
            }
        }
        return builder.toString();
    }

    /** 取会话并校验归属，不存在或越权统一按 404 处理（不泄露「存在但无权」） */
    private BlogAiSessions requireOwnedSession(Owner owner, long sessionId) {
        BlogAiSessions session = aiRepository.findSession(sessionId);
        if (session == null || !isOwnedBy(owner, session)) {
            throw new ResourceNotFoundException("会话不存在或无权访问");
        }
        return session;
    }

    private boolean isOwnedBy(Owner owner, BlogAiSessions session) {
        if (owner.userId() != null && owner.userId().equals(session.getUserId())) {
            return true;
        }
        return session.getUserId() == null && owner.ip().equals(session.getUserIp());
    }

    // ==================== SSE 输出 ====================

    /** 推送一个 SSE 事件；客户端已断开时抛出 {@link ClientGoneException} 以中断上游生成 */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException();
        }
    }

    /** 推送事件但吞掉失败：用于错误分支，此时连接大概率已经断了 */
    private void sendQuietly(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 连接已断，错误提示无处可送
        }
    }

    /** 面向用户的失败原因：业务异常用其原始文案，其余统一兜底，不外泄堆栈 */
    private String friendlyMessage(Exception e) {
        if (e instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return "AI 服务暂时不可用，请稍后重试";
    }

    /** 客户端已断开连接，用于跳出上游读流 */
    private static final class ClientGoneException extends RuntimeException {
    }
}
