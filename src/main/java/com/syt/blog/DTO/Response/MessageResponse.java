package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    Long id;
    Long conversationId;
    Integer senderId;
    Integer receiverId;
    String senderNickname;
    String senderAvatar;
    String content;
    Short isRead;
    LocalDateTime readAt;
    LocalDateTime createdAt;

}
