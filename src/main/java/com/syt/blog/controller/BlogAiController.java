package com.syt.blog.controller;

import com.syt.blog.DTO.Request.AiChatRequest;
import com.syt.blog.DTO.Request.AiSessionRequest;
import com.syt.blog.DTO.Response.AiConfigResponse;
import com.syt.blog.DTO.Response.AiMessageResponse;
import com.syt.blog.DTO.Response.AiSessionResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.Result;
import com.syt.blog.common.AuthUser;
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

/**
 * AI 助手接口。
 * <p>
 * 本模块对游客开放（{@code SecurityConfig} 中已把 {@code /api/ai/**} 设为 {@code permitAll}），
 * 因此这里**不能**调 {@code SecurityUtils.currentUserId()}——匿名访问会抛 401。
 * 正确做法是先看 SecurityContext 里有没有已认证用户，没有就退回按 IP 归属，全程不返回 401。
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
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequest body, HttpServletRequest request) {
        return aiChatService.streamChat(resolveOwner(request), body);
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
     */
    private AiChatService.Owner resolveOwner(HttpServletRequest request) {
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
