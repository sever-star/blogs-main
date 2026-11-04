package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessagesResponse {
    private Long id;
    private PeerUser peerUser;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public record PeerUser(Integer id,String username,String nickname,String avatar){}
}
