package com.syt.blog.service;

import com.syt.blog.DTO.Response.NotificationResponse;
import com.syt.blog.common.AuthUser;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.jooq.tables.pojos.BlogNotifications;
import com.syt.blog.jooq.tables.records.BlogNotificationsRecord;
import com.syt.blog.repository.NotificationRepository;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.impl.QOM;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 站内通知业务：
 * - 查询/已读：仅限本人（currentUserId）。
 * - 生成通知：由评论/点赞等触发点调用 createNotification，传入接收者、触发者、类型与
 *   content 前缀；这里统一解析触发者昵称拼成完整 content，并做防自扰（自己触发自己的动作不发）。
 * 不做聚合：每次动作一条通知，需求未要求合并。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public PageResult list(int page, int pageSize, boolean onlyUnread) {
        Integer userId = SecurityUtils.currentUserId();
        notificationRepository.Insert(userId);
        var records = notificationRepository.findPaged(userId, page, pageSize, onlyUnread);
        List<NotificationResponse> data = records.stream().map(this::toVo).toList();
        long total = notificationRepository.count(userId, onlyUnread);
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        return new PageResult(data, total, page, pageSize, totalPages);
    }

    public Map<String, Long> unreadCount() {
        Integer userId = SecurityUtils.currentUserId();
        notificationRepository.Insert(userId);
        return notificationRepository.countUnread(userId);
    }

    public void markRead(Long id) {
        Integer userId = SecurityUtils.currentUserId();
        int rows = notificationRepository.markRead(id, userId);
        if (rows == 0) {
            throw new IllegalArgumentException("通知不存在或无权操作");
        }
    }

    public void markAllRead() {
        Integer userId = SecurityUtils.currentUserId();
        notificationRepository.markAllRead(userId);
    }

    /**
     * 生成一条通知。
     *
     * @param receiverId    接收者用户 id
     * @param actorId       触发者用户 id
     * @param type          通知类型：post_comment/comment_reply/comment_like/like/follow/message
     * @param title         通知标题
     * @param contentPrefix content 前缀（如「评论了你的文章《Vue3 指南》」），方法内拼上触发者昵称
     * @param link          跳转链接（如 /article/5 或 /article/5#comment-12）
     */
    public void createNotification(Integer receiverId, Integer actorId, String type,
                                   String title, String contentPrefix, String link) {
        if (receiverId == null || actorId == null || actorId.equals(receiverId)) {
            return; // 防自扰：自己对自己的动作不发通知
        }
        String actorNickname = "匿名";
        var actor = userRepository.findById(actorId);
        if (actor != null && actor.getNickname() != null) {
            actorNickname = actor.getNickname();
        }
        String content = actorNickname + " " + contentPrefix;
        notificationRepository.insert(receiverId, type, title, content, link);
    }
    /**
     *删除通知
     * @param id 通知id
     */
    public void delete(Long id) {
        BlogNotifications blogNotifications=notificationRepository.findById(id);
        if(blogNotifications==null){
            throw new BusinessException(404,"通知不存在");
        }
        AuthUser user =SecurityUtils.currentUser();
        if(!"ADMIN".equals(user.role())){
            boolean b = user.id() == blogNotifications.getUserId();
            if (!b){
                throw new BusinessException(404,"通知非本人");
            }
            notificationRepository.delete(id);

        }
        notificationRepository.delete(id);


    }
    private NotificationResponse toVo(BlogNotificationsRecord r) {
        NotificationResponse vo = new NotificationResponse();
        vo.setId(r.getId());
        vo.setType(r.getType());
        vo.setTitle(r.getTitle());
        vo.setContent(r.getContent());
        vo.setLink(r.getLink());
        vo.setIsRead(r.getIsRead());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }


}