package com.syt.blog.repository;

import com.syt.blog.DTO.Request.AnnouncementRequest;
import com.syt.blog.DTO.Response.AnnouncementResponse;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.BlogAnnouncements;
import com.syt.blog.jooq.tables.BlogNotifications;
import com.syt.blog.jooq.tables.records.BlogAnnouncementsRecord;
import jakarta.validation.Valid;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AnnouncementsRepository {
    private final DSLContext dsl;
    private final BlogAnnouncements type=BlogAnnouncements.BLOG_ANNOUNCEMENTS;
    public AnnouncementsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }
    public AnnouncementResponse save(@Valid com.syt.blog.jooq.tables.pojos.BlogAnnouncements blogAnnouncements) {
        BlogAnnouncementsRecord blogAnnouncementsRecord = dsl.insertInto(type).set(type.CONTENT, blogAnnouncements.getContent())
                .set(type.TITLE, blogAnnouncements.getTitle())
                .set(type.CREATED_BY, blogAnnouncements.getCreatedBy()).returning().fetchOne();
        return toVo(blogAnnouncementsRecord);
    }

    public PageResult findAll(int page, int pageSize) {
        //limit设置每页数量---offset设置从第几条开始查询
        List<BlogAnnouncementsRecord> list = dsl.selectFrom(type)
                .orderBy(type.CREATED_AT.desc())
                .limit(pageSize)
                .offset((page - 1) * pageSize)
                .fetchInto(BlogAnnouncementsRecord.class);
        List<AnnouncementResponse> data = list.stream().map(this::toVo).toList();
        int total= dsl.fetchCount(dsl.selectFrom(type));
        int totalPage= pageSize>0?(int)Math.ceil(total/pageSize):0;
        PageResult pageResult = new PageResult(data, total, page, pageSize,totalPage);
        return pageResult;
    }

    @Transactional
    public void update(Long id, com.syt.blog.jooq.tables.pojos.BlogAnnouncements blogAnnouncements) {
        dsl.update(type).set(type.CONTENT,blogAnnouncements.getContent())
                .set(type.TITLE,blogAnnouncements.getTitle())
                .set(type.UPDATED_AT, LocalDateTime.now()).where(type.ID.eq(id)).execute();
        dsl.update(BlogNotifications.BLOG_NOTIFICATIONS)
                .set(BlogNotifications.BLOG_NOTIFICATIONS.CONTENT,blogAnnouncements.getContent())
                .set(BlogNotifications.BLOG_NOTIFICATIONS.TITLE,blogAnnouncements.getTitle())
                .where(BlogNotifications.BLOG_NOTIFICATIONS.SOURCE_ID.eq(id)
                        .and(BlogNotifications.BLOG_NOTIFICATIONS.TYPE.eq("announcement"))).execute();
           }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(type).where(type.ID.eq(id)).execute();
        dsl.deleteFrom(BlogNotifications.BLOG_NOTIFICATIONS)
                .where(BlogNotifications.BLOG_NOTIFICATIONS.SOURCE_ID.eq(id)
                        .and(BlogNotifications.BLOG_NOTIFICATIONS.TYPE.eq("announcement"))).execute();
    }
    public AnnouncementResponse toVo(BlogAnnouncementsRecord blogAnnouncementsRecord) {
        AnnouncementResponse announcementResponse = new AnnouncementResponse();
        announcementResponse.setContent(blogAnnouncementsRecord.getContent());
        announcementResponse.setTitle(blogAnnouncementsRecord.getTitle());
        announcementResponse.setId(blogAnnouncementsRecord.getId());
        announcementResponse.setCreatedBy(blogAnnouncementsRecord.getCreatedBy());
        announcementResponse.setCreatedAt(blogAnnouncementsRecord.getCreatedAt());
        announcementResponse.setUpdatedAt(blogAnnouncementsRecord.getUpdatedAt());
        return announcementResponse;
    }


}
