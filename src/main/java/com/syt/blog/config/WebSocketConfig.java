package com.syt.blog.config;

import com.syt.blog.util.MessagesHandshakeInterceptor;
import com.syt.blog.util.MessagesWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    //
    private final MessagesHandshakeInterceptor handshakeInterceptor;
    private final MessagesWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/messages")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}


