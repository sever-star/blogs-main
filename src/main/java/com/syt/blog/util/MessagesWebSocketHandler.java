package com.syt.blog.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.syt.blog.DTO.Request.MessageRequest;
import com.syt.blog.DTO.Response.MessageResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.service.BlogMessagesService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;

/**
 * 私信 WebSocket 处理器。
 * <p>
 * 协议（JSON 文本帧）：
 * <ul>
 *   <li>客户端→服务端 {@code {type:"send", data:{receiverId, content}}}：发送私信</li>
 *   <li>客户端→服务端 {@code {type:"ping"}}：心跳，回 {@code {type:"pong"}}</li>
 *   <li>服务端→客户端 {@code {type:"send_ack", data:MessageResponse}}：发送成功回执（含落库后的 id/createdAt）</li>
 *   <li>服务端→客户端 {@code {type:"message", data:MessageResponse}}：收到对方发来的消息（推给接收方）</li>
 *   <li>服务端→客户端 {@code {type:"error", code, message}}：发送失败（校验失败/自聊/接收方不存在等）</li>
 *   <li>服务端→客户端 {@code {type:"ping"}}：服务端心跳（25s 一次，由 WebSocketSessionRegistry 发）</li>
 * </ul>
 * <p>
 * <b>发送方身份</b>取自握手时 {@link MessagesHandshakeInterceptor} 写入的 userId，
 * 不信任请求体里除 receiverId/content 之外的任何字段，故无法伪造 senderId。
 * <p>
 * <b>校验</b>：WS 不走 Spring MVC 的 {@code @Valid}，故用 {@link Validator} 手动触发
 * {@link MessageRequest} 上的 Bean Validation 注解（@NotNull/@NotEmpty/@Size）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagesWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final BlogMessagesService messagesService;
    private final JsonMapper objectMapper;
    private final Validator validator;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        registry.register(userId, session);
        log.debug("WS 已连接 userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            send(session, errorJson(400, "请求格式非法"));
            return;
        }

        String type = node.path("type").asText();
        if ("ping".equals(type)) {
            send(session, "{\"type\":\"pong\"}");
            return;
        }
        if (!"send".equals(type)) {
            // 未知类型忽略，避免恶意帧打断连接
            return;
        }

        Integer senderId = (Integer) session.getAttributes().get("userId");
        MessageRequest req;
        try {
            req = objectMapper.treeToValue(node.path("data"), MessageRequest.class);
        } catch (Exception e) {
            send(session, errorJson(400, "请求体非法"));
            return;
        }
        if (req == null) {
            send(session, errorJson(400, "请求体非法"));
            return;
        }

        // WS 不走 @Valid，手动触发 Bean Validation
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            send(session, errorJson(400, violations.iterator().next().getMessage()));
            return;
        }

        try {
            MessageResponse resp = messagesService.senMessageFromWs(senderId, req);
            // 先回 ACK 给发送方（追加自己的气泡），再推接收方
            send(session, wrap("send_ack", resp));
            registry.sendToUser(req.getReceiverId(), wrap("message", resp));
        } catch (BusinessException e) {
            send(session, errorJson(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("WS 发送处理异常", e);
            send(session, errorJson(500, "发送失败"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            registry.remove(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WS 传输异常: {}", exception.getMessage());
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.debug("WS 回写失败: {}", e.getMessage());
            }
        }
    }

    /** 包成 {@code {type:..., data:...}}；MessageResponse 用 ObjectMapper 序列化（与 REST 一致） */
    private String wrap(String type, MessageResponse resp) {
        try {
            return "{\"type\":\"" + type + "\",\"data\":"
                    + objectMapper.writeValueAsString(resp) + "}";
        } catch (Exception e) {
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    private String errorJson(int code, String message) {
        try {
            // 用 ObjectMapper 写字符串，自动做引号转义，避免手写 escape 出错
            return "{\"type\":\"error\",\"code\":" + code + ",\"message\":"
                    + objectMapper.writeValueAsString(message) + "}";
        } catch (Exception e) {
            return "{\"type\":\"error\",\"code\":" + code + ",\"message\":\"\"}";
        }
    }
}
