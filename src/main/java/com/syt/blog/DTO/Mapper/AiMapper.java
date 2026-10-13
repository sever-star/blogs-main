package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Response.AiMessageResponse;
import com.syt.blog.DTO.Response.AiSessionResponse;
import com.syt.blog.jooq.tables.pojos.BlogAiMessages;
import com.syt.blog.jooq.tables.pojos.BlogAiSessions;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AiMapper {

    AiMapper INSTANCE = Mappers.getMapper(AiMapper.class);

    AiSessionResponse blogAiSessionsToAiSessionResponse(BlogAiSessions session);

    AiMessageResponse blogAiMessagesToAiMessageResponse(BlogAiMessages message);
}
