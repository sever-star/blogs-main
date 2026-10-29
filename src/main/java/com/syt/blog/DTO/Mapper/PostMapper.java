package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.jooq.tables.pojos.BlogPosts;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PostMapper {

    PostMapper INSTANCE = Mappers.getMapper(PostMapper.class);

    @Mapping(target="userId", ignore = true)
    // contentHtml 不再忽略：前端提交编辑器渲染好的 HTML（ArticlePayload.contentHtml），随创建一并入库
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "likeCount", ignore = true)
    @Mapping(target = "favCount", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    // created_at/updated_at 都是 NOT NULL + DEFAULT CURRENT_TIMESTAMP，且 updated_at 由
    // trg_posts_updated_at 维护。若映射进来会 setCreatedAt(null)——jOOQ 把「被 set 过的 null」
    // 当作已触碰字段写进 INSERT，显式 NULL 会盖掉默认值 → 违反 NOT NULL。同 blog_users.username 的坑。
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BlogPostsRecord postRequestToBlogPostsRecord(PostRequest postRequest);

}
