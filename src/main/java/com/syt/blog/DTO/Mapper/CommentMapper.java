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

    /** 发布评论的响应：字段名不一致的 postId→articleId 需显式映射，其余同名自动映射 */
    @Mapping(target = "articleId", source = "postId")
    CommentsResponse BlogCommentsToCommentsResponse(BlogComments blogComments);
}
