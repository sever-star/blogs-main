package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.AiMapper;
import com.syt.blog.DTO.Mapper.CategoryMapper;
import com.syt.blog.DTO.Mapper.TagMapper;
import com.syt.blog.DTO.Request.AiChatRequest;
import com.syt.blog.DTO.Response.AiConfigResponse;
import com.syt.blog.DTO.Response.AiMessageResponse;
import com.syt.blog.DTO.Response.AiSessionResponse;
import com.syt.blog.DTO.Response.CategoryResponse;
import com.syt.blog.DTO.Response.TagResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.config.AiProperties;
import com.syt.blog.jooq.tables.pojos.BlogAiMessages;
import com.syt.blog.jooq.tables.pojos.BlogAiSessions;
import com.syt.blog.jooq.tables.pojos.BlogCategories;
import com.syt.blog.jooq.tables.pojos.BlogTags;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.repository.AiRepository;
import com.syt.blog.repository.CategoryRepository;
import com.syt.blog.repository.PostRepository;
import com.syt.blog.repository.TagRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostRepository postRepository;

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
    private static final String REVIEW_SYSTEM_PROMPT = """
            你是文章内容审核助手，为人工审核员提供客观的 AI 预检参考，辅助判断文章是否可以发布。你只做提示和打分，最终是否发布由人工审核员决定。
            
            阅读文章，从以下维度评估并以 JSON 输出（不要输出 JSON 以外的内容）：
            {
              "compliance_score": 0-100,        
              "sensitive_words": ["词1","词2"],
              "quality_score": 0-100,
              "plagiarism_hint": "",
              "summary": "", 
              "suggestion": "pass|reject|manual"
            }
            
            取值规则：
            
            compliance_score：0-100 整数，越高越合规，最低给 5。
            - 涉政、涉黄、暴力、赌博、诈骗、广告导流（联系方式/二维码/外链）、谣言、人身攻击等任一明显违规 → ≤40
            - 仅有轻微语气或表述风险、删改后可发布 → 41-69
            - 合规无风险 → 70-100
            
            sensitive_words：只收录原文原词，不收录编程术语与技术名词；谐音、拆字等变体按原文写法收录；去重；无命中必须给 []，不得编造原文没有的词。
            
            quality_score：0-100 整数，考量结构完整、逻辑清晰、信息真实、无大段 AI 味空话；与合规分和敏感词相互独立，不因审出敏感词而压低。
            
            plagiarism_hint：你无法联网查重，仅基于文本痕迹判断（大段照搬常见教程、AI 生成痕迹过重、前后语气矛盾），不得指认具体抄袭来源；无法判断给 ""，不要猜测。
            
            summary：一句话中文总结文章主题与整体观感，不超过 50 字。
            
            suggestion：三选一——"pass"/"reject"/"manual"，且必须与打分一致：
            - compliance_score ≤40 或 quality_score <40 → "reject"
            - 41≤compliance_score<70，或 40≤quality_score<70，或 plagiarism_hint 非空 → "manual"
            - 其余 → "pass"
            - 打分与 suggestion 不得矛盾；拿不准时优先 "manual"，不要替审核员下结论。
            
            兜底：文章为空、过短或无法评估时，给 compliance_score=50、quality_score=50、suggestion="manual"，并在 summary 中说明原因。
            """;
    /** 会话标题最大长度（表字段 varchar(150)，这里取更适合列表展示的长度） */
    private static final int TITLE_MAX_LENGTH = 30;

    /** 会话列表返回条数上限 */
    private static final int SESSION_LIST_LIMIT = 100;

    /** 消息角色
     * 用户消息：user
     * AI助手消息：assistant
     */
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    // ---- 后台 AI 调用（生成标题/摘要），记录到 blog_ai_usage ----

    /** 发给模型的正文截断上限，控制 token 成本 */
    private static final int MAX_INPUT_CHARS = 6000;
    /** 审计表关联对象类型：文章 */
    private static final String USAGE_REF_TYPE = "post";
    private static final String USAGE_TYPE_TITLE = "title";
    private static final String USAGE_TYPE_SUMMARY = "summary";
    private static final String USAGE_TYPE_TAG = "tag_suggest";
    private static final String USAGE_TYPE_CATEGORY = "category_suggest";
    private static final String USAGE_TYPE_REVIEW = "review_assist";
    /** 标题最大长度（截断模型输出，避免超出表字段 varchar(200)） */
    private static final int TITLE_MAX_OUTPUT_CHARS = 50;
    /** 摘要最大长度（截断模型输出） */
    private static final int SUMMARY_MAX_OUTPUT_CHARS = 120;

    private static final String TITLE_SYSTEM_PROMPT = """
            你是文章标题生成器。阅读正文，生成一个简洁、吸引人的中文标题，不超过 20 字。
            只输出标题文本，不要引号、不要前缀、不要任何解释。
            """;
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是文章摘要生成器。阅读正文，用一句话概括文章主旨，不超过 80 字。
            只输出摘要文本，不要前缀、不要任何解释。
            """;
    /**
     * 标签推荐模板：%s 处由后端拼入站内全部标签（id-name），模型只输出数字 id，
     * 多个 id 用逗号分隔。与分类同构（受控集合：只从存量中选，不自由生成新词）。
     */
    private static final String TAG_SYSTEM_PROMPT = """
            你是文章标签助手。可选标签如下：
            %s
            阅读标题与正文，选出最匹配的 1-2 个标签，只输出其数字 id，用逗号分隔，不要任何解释。
            """;
    /** 分类推荐模板：%s 处拼入站内全部分类（id-name），模型只输出一个数字 id */
    private static final String CATEGORY_SYSTEM_PROMPT = """
            你是博客分类助手。可选分类如下：
            %s
            阅读标题与正文，选出最匹配的一个分类，只输出其数字 id，不要任何解释。
            """;
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


    // ==================== 查询类接口 ====================

    /** AI 能力状态，供前端提示「未配置模型」 */
    public AiConfigResponse getConfig() {
        return new AiConfigResponse(llmClient.isConfigured(), properties.getModel());
    }

    /** 会话列表（最近活跃在前） */
    public List<AiSessionResponse> listSessions(Owner owner) {
        return aiRepository. listSessions(owner, SESSION_LIST_LIMIT).stream()
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
        //对游客进行限流
        if (owner.userId() == null) {
            rateLimiter.acquire(owner.ip());
        }

        //获取提问内容和文件
        String rawContent = request.getContent() == null ? "" : request.getContent().trim();
        List<AiChatRequest.Attachment> attachments = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();
        if (rawContent.isEmpty() && attachments.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_BAD_REQUEST, "提问内容不能为空");
        }

        //判断用户对话是否存在
        long sessionId = resolveSession(owner, request.getSessionId(), rawContent, attachments);
        //把标题和文件内容进行拼接
        String question = buildQuestion(rawContent, attachments);

        // 用户消息状态直接记完成：它不会失败，失败的是后面的助手回复
        aiRepository.insertMessage(sessionId, ROLE_USER, question, AiRepository.STATUS_DONE);
        aiRepository.touchSession(sessionId);

        //组装对话上下文
        List<LlmClient.ChatMessage> context = buildContext(sessionId);
        // 预插助手消息占位，拿到 id 以便流式结束后回填
        long assistantMessageId = aiRepository.insertMessage(
                sessionId, ROLE_ASSISTANT, "", AiRepository.STATUS_GENERATING);
        //设 SSE 超时时间
        SseEmitter emitter = new SseEmitter(Duration.ofSeconds(properties.getTimeoutSeconds() + 30).toMillis());
        // 首帧先告知 id：即使生成中途失败，前端也能把这条消息挂到正确位置并支持重试
        send(emitter, "meta", Map.of("sessionId", sessionId, "messageId", assistantMessageId));

        //创建虚拟线程把runStream任务交给虚拟线程，然后直接下一步
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

    // ==================== 后台 AI 调用（标题/摘要） ====================

    /**
     * 生成文章标题：调模型非流式补全 + 记审计表，返回标题文本。
     * <p>
     * 与流式对话不同：不建会话、不进用户对话列表，只记 blog_ai_usage。
     * 生成时文章尚未落库，refId 传 null。任何失败（含未配置 Key）都记 status=0 后抛出，
     * 由前端提示用户重试或手填——摘要/标题不影响文章提交主流程，但调用本身要有审计。
     */
    public String generateTitle(Integer userId, String ip, String contentMd) {
        String input = truncate(contentMd, MAX_INPUT_CHARS);
        List<LlmClient.ChatMessage> messages = List.of(
                LlmClient.ChatMessage.system(TITLE_SYSTEM_PROMPT),
                LlmClient.ChatMessage.user(input));
        return runAndRecord(USAGE_TYPE_TITLE, userId, ip, input, messages, TITLE_MAX_OUTPUT_CHARS);
    }

    /**
     * 生成文章摘要：可带标题作为上下文，调模型非流式补全 + 记审计表，返回摘要文本。
     */
    public String generateSummary(Integer userId, String ip, String title, String contentMd) {
        String body = truncate(contentMd, MAX_INPUT_CHARS);

        StringBuilder user = new StringBuilder();
        if (title != null && !title.isBlank()) {
            user.append("标题：").append(title).append("\n\n");
        }
        user.append("正文：").append(body);
        String input = user.toString();

        List<LlmClient.ChatMessage> messages = List.of(
                LlmClient.ChatMessage.system(SUMMARY_SYSTEM_PROMPT),
                LlmClient.ChatMessage.user(input));
        return runAndRecord(USAGE_TYPE_SUMMARY, userId, ip, input, messages, SUMMARY_MAX_OUTPUT_CHARS);
    }

    /**
     * AI 推荐标签（受控集合）：把站内全部标签拼入 prompt，模型只输出数字 id（逗号分隔），
     * 后端解析 id → 校验存在 → 返回 id+name。只从存量中选择，不自由生成新词，
     * 因此不会产生「近似新标签」导致标签表膨胀。返回空列表表示无匹配或解析失败。
     */
    public List<TagResponse> suggestTags(Integer userId, String ip, String title, String contentMd) {
        String input = truncate(contentMd, MAX_INPUT_CHARS);
        String options = tagRepository.getTags();
        if (options.isBlank()) {
            return List.of();
        }
        String system = TAG_SYSTEM_PROMPT.formatted(options);
        StringBuilder builder = new StringBuilder();
        if (title != null && !title.isBlank()) {
           builder.append("标题：").append(title).append("\n\n");
        }
        builder.append("正文：").append(input);
        input=builder.toString();
        List<LlmClient.ChatMessage> messages = List.of(
                LlmClient.ChatMessage.system(system),
                LlmClient.ChatMessage.user(input));
        String output = runAndRecord(USAGE_TYPE_TAG, userId, ip, input, messages, properties.getMaxSuggestOutputChars());
        return resolveTags(output);
    }

    /**
     * AI 推荐分类（受控集合）：把站内全部分类拼入 prompt，模型只输出一个数字 id，
     * 后端解析并校验存在。解析失败/越界/不存在 → 返回 null（前端让用户自选）。
     */
    public CategoryResponse suggestCategory(Integer userId, String ip, String title, String contentMd) {
        String input = truncate(contentMd, MAX_INPUT_CHARS);
        String options = categoryRepository.getCategories();
        if (options.isBlank()) {
            return null;
        }
        String system = CATEGORY_SYSTEM_PROMPT.formatted(options);
        StringBuilder builder = new StringBuilder();
        if (title != null && !title.isBlank()) {
            builder.append("标题：").append(title).append("\n\n");
        }
        builder.append("正文：").append(input);
        input=builder.toString();
        List<LlmClient.ChatMessage> messages = List.of(
                LlmClient.ChatMessage.system(system),
                LlmClient.ChatMessage.user(input));
        String output = runAndRecord(USAGE_TYPE_CATEGORY, userId, ip, input, messages, 50);
        Integer categoryId = parseInt(output);
        if (categoryId == null) {
            return null;
        }
        List<BlogCategories> match = categoryRepository.findByIds(List.of(categoryId));
        if (match.isEmpty()) {
            return null;
        }
        return CategoryMapper.INSTANCE.blogCategoriesToCategoryResponse(match.get(0));
    }

    /**
     * 对文章进行审核
     * @param postId
     * @return
     */
    public void getAiAid(Long postId, HttpServletRequest request) {
        Integer userId = SecurityUtils.currentUserId();
        String ip = request.getRemoteAddr();
        BlogPostsRecord post = postRepository.getPostById(Math.toIntExact(postId));
        generateAsync(userId, ip, Math.toIntExact(postId), post.getTitle(), post.getContentMd());
    }

    private void generateAsync(Integer userId, String ip, Integer postId, String title, String contentMd)  {
        final ExecutorService reviewExecutor = Executors.newVirtualThreadPerTaskExecutor();
        reviewExecutor.execute(() -> doGenerate(userId, ip, postId, title, contentMd));
        reviewExecutor.shutdown();
    }
    private void doGenerate(Integer userId, String ip, Integer postId, String title, String contentMd) {
            StringBuilder builder = new StringBuilder();
            if (title != null && !title.isBlank()) {
                builder.append("标题：").append(title).append("\n\n");
            }
            builder.append("正文：").append(contentMd);
            String input = builder.toString();
            List<LlmClient.ChatMessage> messages = List.of(
                    LlmClient.ChatMessage.system(REVIEW_SYSTEM_PROMPT),
                    LlmClient.ChatMessage.user(input));
            try {
                String output = runAndRecord(USAGE_TYPE_REVIEW,userId,ip,input,messages,2000);
                postRepository.updateReview(postId,output,(short)1);
            }catch (Exception e){
                postRepository.updateReview(postId,null,(short)2);
                log.error("审核文章失败",e);
            }

    }

    /** 解析模型输出的多个 id（逗号分隔，含中文逗号）：trim → 去重 → 转为 Integer，非数字跳过 */
    private List<TagResponse> resolveTags(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (String part : output.split("[,，]")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Integer id = parseInt(trimmed);
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<BlogTags> tags = tagRepository.findByIds(new ArrayList<>(ids));
        Map<Integer, BlogTags> byId = tags.stream()
                .collect(Collectors.toMap(BlogTags::getId, t -> t, (a, b) -> a));
        List<TagResponse> result = new ArrayList<>();
        for (Integer id : ids) {
            BlogTags tag = byId.get(id);
            if (tag != null) {
                result.add(TagMapper.INSTANCE.blogTagsToTagResponse(tag));
                if (result.size() >= properties.getMaxSuggestTags()) {
                    break;
                }
            }
        }
        return result;
    }

    /** 解析单个 id，非数字/越界/为空返回 null */
    private Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 通用：调一次非流式补全并把调用记进 blog_ai_usage。
     * <p>
     * 成功 → 记 status=1（含 output/tokens）并返回截断后的输出；
     * 失败 → 记 status=0（含 error_message）后抛出 BusinessException，前端据此提示。
     */
    private String runAndRecord(String type, Integer userId, String ip, String input,
                                List<LlmClient.ChatMessage> messages, int maxOutChars) {
        //记录失败数据
        if (!llmClient.isConfigured()) {
            aiRepository.insertUsage(userId, ip, type, USAGE_REF_TYPE, null, input, null,
                    0, properties.getModel(), AiRepository.STATUS_FAILED, "AI 服务未配置");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 服务未配置");
        }
        try {
            LlmClient.Completion completion = llmClient.complete(messages);
            String output = completion.content() == null ? "" : completion.content().trim();
            if (output.length() > maxOutChars) {
                output = output.substring(0, maxOutChars);
            }
            aiRepository.insertUsage(userId, ip, type, USAGE_REF_TYPE, null, input, output,
                    completion.tokens(), properties.getModel(), AiRepository.STATUS_DONE, null);
            return output;
        } catch (Exception e) {
            String reason = e instanceof BusinessException be ? be.getMessage() : e.getMessage();
            aiRepository.insertUsage(userId, ip, type, USAGE_REF_TYPE, null, input, null,
                    0, properties.getModel(), AiRepository.STATUS_FAILED, reason);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 生成失败：" + e.getMessage());
        }
    }

    /** 截断字符串到最大长度，null 视为空串 */
    private static String truncate(String s, int max) {

        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
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


