package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.PostCommentRequest;
import com.syt.blog.DTO.Response.CommentsResponse;
import com.syt.blog.jooq.tables.pojos.BlogComments;
import org.hibernate.annotations.Comments;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CommentMapper {
    CommentMapper INSTANCE = Mappers.getMapper(CommentMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "content", source = "content")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "replyToId", source = "replyToId")
    BlogComments createComment(PostCommentRequest postCommentRequest);

    CommentsResponse BlogCommentsToCommentsResponse(BlogComments blogComments);
}
