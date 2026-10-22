package com.syt.blog.controller;

import com.syt.blog.DTO.Request.AnnouncementRequest;
import com.syt.blog.DTO.Response.AnnouncementResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.service.BlogAnnouncementsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class BlogAnnouncementsController {
    private final BlogAnnouncementsService blogAnnouncementsService;

    public BlogAnnouncementsController(BlogAnnouncementsService blogAnnouncementsService) {
        this.blogAnnouncementsService = blogAnnouncementsService;
    }

    /**
     * 添加公告
     */
    @PostMapping
    public Result<AnnouncementResponse> addAnnouncement(@RequestBody @Valid AnnouncementRequest announcementRequest) {
        AnnouncementResponse announcementResponse = blogAnnouncementsService.addAnnouncement(announcementRequest);
        return Result.success(announcementResponse);
    }
    /**
     * 获取所有公告
     */
    @GetMapping
    public Result<PageResult> getAnnouncements(@RequestParam(defaultValue = "1") int page
            , @RequestParam(defaultValue = "10") int pageSize) {
        PageResult pageResult = blogAnnouncementsService.getAnnouncements(page, pageSize);
        return Result.success(pageResult);
    }
    /**
     * 更新公告
     */
    @PutMapping("/{id}")
    public Result updateAnnouncement(@PathVariable Long id, @RequestBody @Valid AnnouncementRequest announcementRequest) {
        blogAnnouncementsService.updateAnnouncement(id, announcementRequest);
        return Result.success();
    }
    /**
     * 删除公告
     */
    @DeleteMapping("/{id}")
    public Result deleteAnnouncement(@PathVariable Long id) {
        blogAnnouncementsService.deleteAnnouncement(id);
        return Result.success();
    }
}
