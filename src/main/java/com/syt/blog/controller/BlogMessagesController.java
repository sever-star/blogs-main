package com.syt.blog.controller;

import com.syt.blog.DTO.Request.MessageRequest;
import com.syt.blog.DTO.Response.MessageResponse;
import com.syt.blog.DTO.Response.MessagesResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.service.BlogMessagesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class BlogMessagesController {
    private final BlogMessagesService blogMessagesService;

    /**
     * 获取我的会话列表
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/conversations")
    public Result<PageResult> messages(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(blogMessagesService.messagesPage(page,pageSize));
    }

    /**
     * 创建或获取和某用户的会话
     * @param userId
     * @return
     */
    @GetMapping("/conversations/with/{userId}")
    public Result<MessagesResponse> userMessage(@PathVariable Integer userId){
        return Result.success(blogMessagesService.findMessage(userId));
    }

    /**
     * 获取会话中的消息列表
     * @param conversationId
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public  Result<PageResult> messageList(@PathVariable Long conversationId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer pageSize){
        return Result.success(blogMessagesService.findList(conversationId,page,pageSize));
    }

    /**
     * 发送私信
     * 没有创建会话
     * @param messageRequest
     * @return
     */
    @PostMapping
    public Result<MessageResponse> sedMessage(@RequestBody @Valid MessageRequest messageRequest){
        return Result.success(blogMessagesService.senMessage(messageRequest));
    }

    /**
     * 标记会话已读
     * @param conversationId
     * @return
     */
    @PutMapping("/conversations/{conversationId}/read")
    public Result<Map<String,Integer>> readMessage(@PathVariable Long conversationId){
        Map<String,Integer> map=new HashMap<>();
        Integer unreadCount=blogMessagesService.readMessage(conversationId);
        map.put("unreadCount",unreadCount);
        return Result.success(map);
    }

    /**
     * 删除会话。对自己隐藏
     * @param conversationId
     * @return
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result deleteMessage(@PathVariable Long conversationId){
        blogMessagesService.deleteMessage(conversationId);
        return  Result.success();
    }

    /**
     *
     * @return
     */
    @GetMapping("/unread-count")
    public Result<Map<String,Integer>> unreadCount(){
        Map<String,Integer> map=new HashMap<>();
        Integer count= blogMessagesService.countUnread();
        map.put("count",count);
        return Result.success(map);
    }
}
