package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.TagRequest;
import com.syt.blog.DTO.Response.TagResponse;
import com.syt.blog.jooq.tables.pojos.BlogTags;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TagMapper {
    TagMapper INSTANCE= Mappers.getMapper(TagMapper.class);

    TagResponse blogTagsToTagResponse(BlogTags blogTags);

    @Mapping(target = "createdAt", ignore = true)
    BlogTags blogTagsToBlogTagRequest(TagRequest tagRequest);
}
