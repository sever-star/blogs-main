package com.syt.blog.DTO.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagesRequest {
    Integer id;
    Integer conversationId;
    Integer senderId;
    Integer receiverId;
    String senderNickname;
    String senderAvatar;
    String content;
    String isRead;
    String readAt;
    String createdAt;

}
