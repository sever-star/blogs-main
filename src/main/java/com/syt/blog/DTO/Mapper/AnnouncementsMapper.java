package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.AnnouncementRequest;
import com.syt.blog.DTO.Response.AnnouncementResponse;
import com.syt.blog.jooq.tables.pojos.BlogAnnouncements;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AnnouncementsMapper {
    AnnouncementsMapper INSTANCE = Mappers.getMapper(AnnouncementsMapper.class);

    BlogAnnouncements toAnnouncementRequest(AnnouncementRequest announcementRequest);
}
