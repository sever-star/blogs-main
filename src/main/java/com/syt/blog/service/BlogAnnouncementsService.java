package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.AnnouncementsMapper;
import com.syt.blog.DTO.Request.AnnouncementRequest;
import com.syt.blog.DTO.Response.AnnouncementResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.pojos.BlogAnnouncements;
import com.syt.blog.jooq.tables.records.BlogAnnouncementsRecord;
import com.syt.blog.repository.AnnouncementsRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.validation.Valid;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class BlogAnnouncementsService {
    private final AnnouncementsRepository announcementsRepository;
    private final DSLContext dsl;
    public BlogAnnouncementsService(AnnouncementsRepository announcementsRepository, DSLContext dsl) {
        this.announcementsRepository = announcementsRepository;
        this.dsl = dsl;
    }
    public AnnouncementResponse addAnnouncement( AnnouncementRequest announcementRequest) {
        BlogAnnouncements blogAnnouncements = AnnouncementsMapper.INSTANCE.toAnnouncementRequest(announcementRequest);
        blogAnnouncements.setCreatedBy(SecurityUtils.currentUserId());
        return announcementsRepository.save(blogAnnouncements);
    }

    public void updateAnnouncement(Long id,  AnnouncementRequest announcementRequest) {
        BlogAnnouncements blogAnnouncements = AnnouncementsMapper.INSTANCE.toAnnouncementRequest(announcementRequest);
        announcementsRepository.update(id, blogAnnouncements);
    }

    public void deleteAnnouncement(Long id) {
        announcementsRepository.deleteById(id);
    }

    public PageResult getAnnouncements(int page, int pageSize) {
        return announcementsRepository.findAll(page,pageSize);
    }
}