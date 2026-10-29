package com.syt.blog.controller;

import com.syt.blog.DTO.Request.AiChatRequest;
import com.syt.blog.DTO.Request.AiGenerateRequest;
import com.syt.blog.DTO.Request.AiSessionRequest;
import com.syt.blog.DTO.Response.AiConfigResponse;
import com.syt.blog.DTO.Response.AiMessageResponse;
import com.syt.blog.DTO.Response.AiSessionResponse;
import com.syt.blog.common.*;
import com.syt.blog.service.AiChatService;
import com.syt.blog.util.OssUtil;
import com.syt.blog.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 助手接口。
 * <p>
 * 本模块对游客开放（{@code SecurityConfig} 中已把 {@code /api/ai/**} 设为 {@code permitAll}），
 * 因此这里**不能**调 {@code SecurityUtils.currentUserId()}——匿名访问会抛 401。
 * 正确做法是先看 SecurityContext 里有没有已认证用户，没有就退回按 IP 归属。
 * <p>
 * 「不返回 401」只对**真游客**成立；若请求带了 token 却失效（过期/无效/禁用），
 * {@link JwtAuthenticationFilter} 会写 {@link AuthFailure} 但不写认证、继续放行——
 * 这类请求若静默降级为匿名，会命中不了 user_id 绑定的会话而误报「会话不存在或无权访问」。
 * 故在 {@link #resolveOwner} 里先看 {@link AuthFailure}：非空说明是失效 token，主动抛业务异常
 * 交全局处理器映射 401/403，由前端刷新 token 后重试。真游客（无 Authorization 头）AuthFailure 为 null，照常进入。
 * 成本控制交给 {@link AiChatService} 内的游客限流。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class BlogAiController {

    /** user_ip 字段长度 varchar(45)，转发头可能带脏数据，落库前截断 */
    private static final int IP_MAX_LENGTH = 45;

    /** 附件大小上限：10MB（与 application-dev.yaml 的 multipart 限制保持一致） */
    private static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;

    private final AiChatService aiChatService;
    private final OssUtil ossUtil;

    /** AI 能力状态：前端据此在未配置模型时给出提示 */
    @GetMapping("/config")
    public Result<AiConfigResponse> config() {
        return Result.success(aiChatService.getConfig());
    }

    /** 会话列表 */
    @GetMapping("/sessions")
    public Result<List<AiSessionResponse>> listSessions(HttpServletRequest request) {
        return Result.success(aiChatService.listSessions(resolveOwner(request)));
    }

    /** 新建会话 */
    @PostMapping("/sessions")
    public Result<AiSessionResponse> createSession(@Valid @RequestBody(required = false) AiSessionRequest body,
                                                   HttpServletRequest request) {
        String title = body == null ? null : body.getTitle();
        return Result.success(aiChatService.createSession(resolveOwner(request), title));
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable long id, HttpServletRequest request) {
        aiChatService.deleteSession(resolveOwner(request), id);
        return Result.success();
    }

    /** 会话历史消息 */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AiMessageResponse>> listMessages(@PathVariable long id, HttpServletRequest request) {
        return Result.success(aiChatService.listMessages(resolveOwner(request), id));
    }

    /**
     * 流式对话（SSE）。
     * <p>
     * 返回的事件：{@code meta} / {@code content} / {@code done} / {@code error}。
     * 由于 EventSource 只支持 GET，前端用 fetch + ReadableStream 消费本接口。
     */
    @PostMapping(value = "/chat")
    public SseEmitter chat(@Valid @RequestBody AiChatRequest body, HttpServletRequest request) {
        // token 失效（过期/无效/禁用）的拦截统一在 resolveOwner 里，覆盖所有 AI 接口
        return aiChatService.streamChat(resolveOwner(request), body);
    }

    /**
     * 生成文章标题（非流式）。需登录：写作为登录态功能，游客直接 401。
     * <p>
     * 失效 token 由 {@link #resolveOwner} 抛 401/403，前端走 axios 自动刷新重试。
     * 调用记入 {@code blog_ai_usage}（type=title），生成时文章未落库，ref_id 为空。
     */
    @PostMapping("/generate-title")
    public Result<Map<String, String>> generateTitle(@Valid @RequestBody AiGenerateRequest body,
                                                     HttpServletRequest request) {
        AiChatService.Owner owner = resolveOwner(request);
        if (owner.userId() == null) {
            throw new BusinessException(401, "请先登录后使用 AI 生成");
        }
        String title = aiChatService.generateTitle(owner.userId(), owner.ip(), body.getContentMd());
        return Result.success(Map.of("title", title));
    }

    /**
     * 生成文章摘要（非流式）。需登录；可带标题作为上下文。
     * 调用记入 {@code blog_ai_usage}（type=summary）。
     */
    @PostMapping("/generate-summary")
    public Result<Map<String, String>> generateSummary(@Valid @RequestBody AiGenerateRequest body,
                                                       HttpServletRequest request) {
        AiChatService.Owner owner = resolveOwner(request);
        if (owner.userId() == null) {
            throw new BusinessException(401, "请先登录后使用 AI 生成");
        }
        String summary = aiChatService.generateSummary(owner.userId(), owner.ip(),
                body.getTitle(), body.getContentMd());
        return Result.success(Map.of("summary", summary));
    }

    /** 上传二进制附件（图片等）到 OSS，返回公开地址 */
    @PostMapping("/upload")
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > ATTACHMENT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.AI_BAD_REQUEST, "附件不能超过 10MB");
        }
        String url;
        try {
            url = ossUtil.uploadFile(file, "ai-attachment");
        } catch (Exception e) {
            log.warn("AI 附件上传失败", e);
            throw new BusinessException(ErrorCode.AI_BAD_REQUEST, "附件上传失败：" + e.getMessage());
        }
        return Result.success(new UploadResult(url, file.getOriginalFilename(), file.getSize()));
    }

    // ==================== 归属解析 ====================

    /**
     * 归属解析：已认证取 SecurityContext 里的用户 ID，否则为 null（按 IP 归属）。
     * <p>
     * 必须用 {@link SecurityUtils#currentUser()}（可空版），**不能**用会抛 401 的
     * {@code currentUserId()}——本接口 permitAll，匿名是正常路径，不是错误。
     * <p>
     * 但「带了 token 却失效」（过期/无效/禁用）不算真游客——这类请求的会话绑在 user_id 上，
     * 若静默降级为匿名，会命中不了归属而误报「会话不存在或无权访问」。故先看 {@link AuthFailure}：
     * 非空说明是失效 token，主动抛业务异常交全局处理器映射 401/403，让前端刷新 token 后重试；
     * 真游客（无 Authorization 头）AuthFailure 为 null，照常以游客身份进入。
     */
    private AiChatService.Owner resolveOwner(HttpServletRequest request) {
        AuthFailure failure = AuthFailure.of(request);
        if (failure != null) {
            throw new BusinessException(failure.code(), failure.message());
        }
        AuthUser user = SecurityUtils.currentUser();
        Integer userId = user == null ? null : user.id();
        return new AiChatService.Owner(userId, clientIp(request));
    }

    /**
     * 取真实客户端 IP。
     * <p>
     * 经 Vite 代理 / nginx 转发后 remoteAddr 是代理地址，需优先读转发头；
     * X-Forwarded-For 是逗号分隔的链路，第一段才是原始客户端。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncateIp(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncateIp(realIp.trim());
        }
        return truncateIp(request.getRemoteAddr());
    }

    private String truncateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        return ip.length() > IP_MAX_LENGTH ? ip.substring(0, IP_MAX_LENGTH) : ip;
    }

    /** 附件上传结果 */
    public record UploadResult(String url, String name, long size) {
    }
}
