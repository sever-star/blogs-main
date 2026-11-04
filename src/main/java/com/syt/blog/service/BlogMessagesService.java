package com.syt.blog.service;

import com.syt.blog.DTO.Request.MessageRequest;
import com.syt.blog.DTO.Response.MessageResponse;
import com.syt.blog.DTO.Response.MessagesResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.repository.MessagesRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogMessagesService {
    private final MessagesRepository messagesRepository;
    private final NotificationService notificationService;

    public PageResult messagesPage(Integer page, Integer pageSize) {
        Integer userId= SecurityUtils.currentUserId();
        List<MessagesResponse> list=messagesRepository.findConversations(userId,page,pageSize,null);
        long total=messagesRepository.countConversations(userId);
        PageResult pageResult=new PageResult();
        pageResult.setData(list);
        pageResult.setTotal(total);
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        int totalPage= pageSize>0?(int)Math.ceil(total/pageSize):0;
        pageResult.setTotalPages(totalPage);
        return pageResult;
    }

    public MessagesResponse findMessage(Integer peerUserId) {
        Integer currentUserId= SecurityUtils.currentUserId();
        if(currentUserId.equals(peerUserId)){
            throw new BusinessException(400,"不能与自己会话");
        }
        return messagesRepository.findMessage(peerUserId,currentUserId);
    }

    public PageResult findList(Long conversationId, Integer page, Integer pageSize) {
        Integer currentUserId= SecurityUtils.currentUserId();
        List<MessageResponse> list= messagesRepository.findList(conversationId,currentUserId,page,pageSize);
        PageResult pageResult=new PageResult();
        Integer total=messagesRepository.messageCount(conversationId,currentUserId);
        pageResult.setData(list);
        pageResult.setTotal(total);
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        int totalPage= pageSize>0?(int)Math.ceil(total/pageSize):0;
        pageResult.setTotalPages(totalPage);
        return pageResult;
    }

    public MessageResponse senMessage(@Valid MessageRequest messageRequest) {
        Integer currentUserId= SecurityUtils.currentUserId();
        Integer receiverId= messageRequest.getReceiverId();
        if(currentUserId.equals(receiverId)){
            throw new BusinessException(400,"不能与自己会话");
        }
        MessageResponse messageResponse= messagesRepository.addMessage(currentUserId,messageRequest);
        return messageResponse;
    }

    /**
     * WebSocket 发送私信入口。
     * <p>
     * 与 {@link #senMessage} 的区别：REST 路径靠 {@code SecurityUtils.currentUserId()}
     * 从 SecurityContext 取发送方；WS 帧处理阶段没有 HTTP 请求上下文，SecurityContext 为空，
     * 故发送方身份由 {@link com.syt.blog.util.MessagesHandshakeInterceptor} 在握手时写入
     * session attributes，Handler 显式传入。校验已在 Handler 用 Validator 完成，这里只做自聊拦截 + 落库。
     */
    public MessageResponse senMessageFromWs(Integer senderId, MessageRequest messageRequest) {
        Integer receiverId = messageRequest.getReceiverId();
        if (senderId.equals(receiverId)) {
            throw new BusinessException(400, "不能与自己会话");
        }
        return messagesRepository.addMessage(senderId, messageRequest);
    }

    public Integer readMessage(Long conversationId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        return messagesRepository.updateRead(currentUserId,conversationId);
    }

    public void deleteMessage(Long conversationId) {
        Integer currentUserId = SecurityUtils.currentUserId();
        messagesRepository.deleteMessage(currentUserId,conversationId);
    }

    public Integer countUnread() {
        Integer currentUserId = SecurityUtils.currentUserId();
        return messagesRepository.countUnread(currentUserId);
    }
}
