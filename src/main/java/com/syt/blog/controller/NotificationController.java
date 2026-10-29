package com.syt.blog.controller;

import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

/**
 * 站内通知接口（均需登录，仅本人可见自己的通知）。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 通知列表（分页，可只看未读） */
    @GetMapping
    public Result<PageResult> list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int pageSize,
                                   @RequestParam(defaultValue = "false") boolean onlyUnread) {
        return Result.success(notificationService.list(page, pageSize, onlyUnread));
    }

    /** 未读数（供顶部铃铛徽标） */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        Map<String, Long> byType  = notificationService.unreadCount();
        Long total=0l;
        for(Long l:byType.values()){
           if (l!=null)total+=l;
        }
        return Result.success(Map.of(
                "count",total,
                "byType",byType
        ));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.success();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Result.success();
    }
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }
}
