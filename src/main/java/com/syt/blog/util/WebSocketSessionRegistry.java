package com.syt.blog.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户的 WebSocket 会话注册表。
 * <p>
 * 一个用户可能多端登录，故每个用户用 {@code Set<WebSocketSession>} 收纳多条连接。
 * {@code ConcurrentHashMap} + {@code ConcurrentHashMap.newKeySet()} 保证并发安全；
 * {@code WebSocketSession.sendMessage} 本身非线程安全，按 session 维度 synchronized 发送。
 * <p>
 * 推送失败（连接已断/IO 异常）即把该 session 摘除，避免注册表堆积死连接；
 * 心跳定时 ping 进一步清理半开连接。
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private static final String PING = "{\"type\":\"ping\"}";

    private final Map<Integer, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Integer userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Integer userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            sessions.remove(userId);
        }
    }

    public boolean isOnline(Integer userId) {
        return sessions.containsKey(userId) && !sessions.get(userId).isEmpty();
    }

    /** 给某用户的所有在线 session 推送文本；不在线则静默跳过（不影响发送主流程）。 */
    public void sendToUser(Integer userId, String json) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                send(s, message, userId);
            }
        }
    }

    /**
     * 心跳：定时给所有在线 session 发 ping，防反代 idle 断连；
     * 顺带让send失败时清理死session
     */
    @Scheduled(fixedDelay = 25_000L, initialDelay = 25_000L)
    public void heartbeat() {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage ping = new TextMessage(PING);
        sessions.forEach((userId, set) -> {
            for (WebSocketSession s : set) {
                if (s.isOpen()) {
                    send(s, ping, userId);
                }
            }
        });
    }

    private void send(WebSocketSession session, TextMessage message, Integer userId) {
        synchronized (session) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.debug("WS 推送失败，摘除 session userId={}: {}", userId, e.getMessage());
                remove(userId, session);
            }
        }
    }
}
