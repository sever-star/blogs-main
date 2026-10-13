package com.syt.blog.repository;

import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.CategoryResponse;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.DTO.Response.TagResponse;

import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.BlogPosts;
import com.syt.blog.jooq.tables.records.BlogPostTagsRecord;
import com.syt.blog.jooq.tables.records.BlogPostsRecord;
import com.syt.blog.util.SecurityUtils;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static com.syt.blog.jooq.tables.BlogCategories.BLOG_CATEGORIES;
import static com.syt.blog.jooq.tables.BlogPostFavorites.BLOG_POST_FAVORITES;
import static com.syt.blog.jooq.tables.BlogPostLikes.BLOG_POST_LIKES;
import static com.syt.blog.jooq.tables.BlogPostTags.BLOG_POST_TAGS;
import static com.syt.blog.jooq.tables.BlogPosts.BLOG_POSTS;
import static com.syt.blog.jooq.tables.BlogTags.BLOG_TAGS;
import static com.syt.blog.jooq.tables.BlogUsers.BLOG_USERS;

@Repository
public class PostRepository {
    private final DSLContext dsl;

    public PostRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public BlogPostsRecord getPostById(Integer postId) {
        return dsl.selectFrom(BLOG_POSTS).where(BLOG_POSTS.ID.eq(postId)).fetchOne();
    }

    public PostResponse getPostAndCategory(Integer postid) {
        var VO=dsl.select(
                BLOG_POSTS.ID,
                BLOG_POSTS.TITLE,
                BLOG_POSTS.SUMMARY,
                BLOG_POSTS.CONTENT_MD,
                BLOG_POSTS.COVER_IMAGE,
                BLOG_POSTS.USER_ID,
                BLOG_USERS.NICKNAME.as("authorNickname"),   // 作者昵称：用别名平铺
                BLOG_POSTS.READING_TIME,
                BLOG_POSTS.VIEW_COUNT,
                BLOG_POSTS.LIKE_COUNT,
                BLOG_POSTS.FAV_COUNT,
                BLOG_POSTS.STATUS,
                BLOG_POSTS.ALLOW_COMMENT,
                BLOG_POSTS.IS_TOP,
                BLOG_POSTS.CREATED_AT,
                BLOG_POSTS.UPDATED_AT,
                // 分类：嵌套对象用点号别名
                BLOG_CATEGORIES.ID.as("category.id"),
                BLOG_CATEGORIES.NAME.as("category.name")
        ).from(BLOG_POSTS).join(BLOG_USERS).on(BLOG_POSTS.USER_ID.eq(BLOG_USERS.ID))
                .leftJoin(BLOG_CATEGORIES).on(BLOG_POSTS.CATEGORY_ID.eq(BLOG_CATEGORIES.ID))
                .where(BLOG_POSTS.ID.eq(postid)).fetchOne();


        List<TagResponse> tags = dsl.select(
                BLOG_TAGS.ID,
                BLOG_TAGS.NAME,
                BLOG_TAGS.CREATED_AT
        ).from(BLOG_POST_TAGS).join(BLOG_TAGS).on(BLOG_POST_TAGS.TAG_ID.eq(BLOG_TAGS.ID))
                .where(BLOG_POST_TAGS.POST_ID.eq(postid)) .fetchInto(TagResponse.class);
        PostResponse postResponse= VO.into(PostResponse.class);
        CategoryResponse categoryResponse = new CategoryResponse();
        categoryResponse.setId(VO.get("category.id",Integer.class));
        categoryResponse.setName(VO.get("category.name",String.class));
        postResponse.setCategory(categoryResponse);
        postResponse.setTags(tags);
        return postResponse;
    }


    public List<PostResponse> findPostCond(Integer categoryId, Integer tagId, String keyword) {
        // 构建查询条件
        Condition noCondition = DSL.noCondition();
        if (categoryId != null) {
            noCondition = noCondition.and(BLOG_POSTS.CATEGORY_ID.eq(categoryId));
        }
        if (keyword != null) {
            noCondition = noCondition.and(BLOG_POSTS.TITLE.like("%" + keyword + "%"));
        }
        var VO=dsl.select(
                BLOG_POSTS.ID,
                BLOG_POSTS.USER_ID,
                BLOG_POSTS.TITLE,
                BLOG_POSTS.SUMMARY,
                BLOG_POSTS.CONTENT_MD,
                BLOG_POSTS.COVER_IMAGE,
                BLOG_POSTS.READING_TIME,
                BLOG_POSTS.VIEW_COUNT,
                BLOG_POSTS.LIKE_COUNT,
                BLOG_POSTS.FAV_COUNT,
                BLOG_POSTS.STATUS,
                BLOG_POSTS.ALLOW_COMMENT,
                BLOG_POSTS.IS_TOP,
                BLOG_POSTS.CREATED_AT,
                BLOG_POSTS.UPDATED_AT,
                // 分类：嵌套对象用点号别名
                BLOG_CATEGORIES.ID.as("category.id"),
                BLOG_CATEGORIES.NAME.as("category.name"),
                BLOG_USERS.NICKNAME.as("authorNickname"),// 作者昵称：用别名平铺
                BLOG_USERS.AVATAR.as("authorAvatar")// 作者头像：用别名平铺
        ).from(BLOG_POSTS).join(BLOG_USERS).on(BLOG_POSTS.USER_ID.eq(BLOG_USERS.ID)).
                leftJoin(BLOG_CATEGORIES).on(BLOG_POSTS.CATEGORY_ID.eq(BLOG_CATEGORIES.ID)).
                // 公开列表只回已发布（status=1）且未软删的——草稿(0)/已下架(2)不外露，与 API 文档一致
                where(noCondition)
                        .and(BLOG_POSTS.STATUS.eq((short) 1))
                        .and(BLOG_POSTS.DELETED_AT.isNull())
                .fetch(r->{
                    PostResponse  postResponse = r.into(PostResponse.class);
                    CategoryResponse categoryResponse = new CategoryResponse();
                    categoryResponse.setId(r.get("category.id",Integer.class));
                    categoryResponse.setName(r.get("category.name",String.class));
                    if (categoryResponse != null&&postResponse.getId()!=null) {
                        postResponse.setCategory(categoryResponse);
                    }
                    return postResponse;
                });

        // 获取所有查询到的帖子ID
        List<Integer> postIds = VO.stream().map(PostResponse::getId).toList();
        Condition tagCondition = DSL.noCondition();
        if (tagId != null) {
            tagCondition = tagCondition.and(BLOG_POST_TAGS.TAG_ID.eq(tagId));
        }
        Map<Integer, List<TagResponse>> tags = dsl.select(
                BLOG_TAGS.ID,
                BLOG_TAGS.NAME,
                BLOG_TAGS.CREATED_AT,
                BLOG_POST_TAGS.POST_ID
        ).from(BLOG_TAGS).join(BLOG_POST_TAGS).on(BLOG_TAGS.ID.eq(BLOG_POST_TAGS.TAG_ID)).
                where(tagCondition).and(BLOG_POST_TAGS.POST_ID.in(postIds)).fetchGroups(
                    r -> r.get(BLOG_POST_TAGS.POST_ID),
                    r -> r.into(TagResponse.class)
                );
        VO.forEach(p->p.setTags(tags.getOrDefault(p.getId(), List.of())));
        return VO;
    }

    /**
     * 我的文章：本人全部稿件（草稿/待审核/已发布），未软删，按更新时间倒序。
     * 与 findPostCond 的区别：按 USER_ID 过滤且不过滤状态——草稿只有作者能看到。
     */
    public List<PostResponse> findMyPosts(Integer userId) {
        var VO = dsl.select(
                BLOG_POSTS.ID,
                BLOG_POSTS.USER_ID,
                BLOG_POSTS.TITLE,
                BLOG_POSTS.SUMMARY,
                BLOG_POSTS.COVER_IMAGE,
                BLOG_POSTS.READING_TIME,
                BLOG_POSTS.VIEW_COUNT,
                BLOG_POSTS.LIKE_COUNT,
                BLOG_POSTS.FAV_COUNT,
                BLOG_POSTS.STATUS,
                // 审核状态与发布状态是两个维度：status=0/1/2（草稿/已发布/已下架），
                // review_status=0/1/2（待审核/通过/拒绝），我的文章页两者都要展示
                BLOG_POSTS.REVIEW_STATUS.as("reviewStatus"),
                BLOG_POSTS.ALLOW_COMMENT,
                BLOG_POSTS.IS_TOP,
                BLOG_POSTS.CREATED_AT,
                BLOG_POSTS.UPDATED_AT,
                BLOG_CATEGORIES.ID.as("category.id"),
                BLOG_CATEGORIES.NAME.as("category.name"),
                BLOG_USERS.NICKNAME.as("authorNickname"),
                BLOG_USERS.AVATAR.as("authorAvatar")
        ).from(BLOG_POSTS).join(BLOG_USERS).on(BLOG_POSTS.USER_ID.eq(BLOG_USERS.ID))
                .leftJoin(BLOG_CATEGORIES).on(BLOG_POSTS.CATEGORY_ID.eq(BLOG_CATEGORIES.ID))
                .where(BLOG_POSTS.USER_ID.eq(userId).and(BLOG_POSTS.DELETED_AT.isNull()))
                .orderBy(BLOG_POSTS.UPDATED_AT.desc())
                .fetch(r -> {
                    PostResponse postResponse = r.into(PostResponse.class);
                    postResponse.setReviewStatus(r.get("reviewStatus", Short.class));
                    CategoryResponse categoryResponse = new CategoryResponse();
                    categoryResponse.setId(r.get("category.id", Integer.class));
                    categoryResponse.setName(r.get("category.name", String.class));
                    postResponse.setCategory(categoryResponse);
                    return postResponse;
                });

        List<Integer> postIds = VO.stream().map(PostResponse::getId).toList();
        if (postIds.isEmpty()) {
            return VO;
        }
        Map<Integer, List<TagResponse>> tags = dsl.select(
                BLOG_TAGS.ID,
                BLOG_TAGS.NAME,
                BLOG_TAGS.CREATED_AT,
                BLOG_POST_TAGS.POST_ID
        ).from(BLOG_TAGS).join(BLOG_POST_TAGS).on(BLOG_TAGS.ID.eq(BLOG_POST_TAGS.TAG_ID))
                .where(BLOG_POST_TAGS.POST_ID.in(postIds)).fetchGroups(
                        r -> r.get(BLOG_POST_TAGS.POST_ID),
                        r -> r.into(TagResponse.class)
                );
        VO.forEach(p -> p.setTags(tags.getOrDefault(p.getId(), List.of())));
        return VO;
    }

    public BlogPostDetailResponse findByIdPost(Integer postId) {
        BlogPostDetailResponse vo= dsl.select(BLOG_POSTS.ID,
                BLOG_POSTS.TITLE,
                BLOG_POSTS.SUMMARY,
                BLOG_POSTS.CONTENT_MD,
                BLOG_POSTS.CONTENT_HTML,
                BLOG_POSTS.COVER_IMAGE,
                BLOG_POSTS.READING_TIME,
                BLOG_POSTS.VIEW_COUNT,
                BLOG_POSTS.LIKE_COUNT,
                BLOG_POSTS.FAV_COUNT,
                BLOG_POSTS.STATUS,
                BLOG_POSTS.ALLOW_COMMENT,
                BLOG_POSTS.IS_TOP,
                BLOG_POSTS.CREATED_AT,
                BLOG_POSTS.UPDATED_AT,
                BLOG_CATEGORIES.ID.as("category.id"),
                BLOG_CATEGORIES.NAME.as("category.name"),
                BLOG_USERS.NICKNAME.as("authorNickname"),
                BLOG_USERS.AVATAR.as("authorAvatar"),
                BLOG_USERS.ID.as("userId")
                ).from(BLOG_POSTS).join(BLOG_USERS).on(BLOG_POSTS.USER_ID.eq(BLOG_USERS.ID)).
                leftJoin(BLOG_CATEGORIES).on(BLOG_POSTS.CATEGORY_ID.eq(BLOG_CATEGORIES.ID)).
                where(BLOG_POSTS.ID.eq(postId)).fetchOne(r->{
                    BlogPostDetailResponse blogPostDetailResponse = r.into(BlogPostDetailResponse.class);
                    CategoryResponse categoryResponse =new CategoryResponse();
                    categoryResponse.setId(r.get("category.id", Integer.class));
                    categoryResponse.setName(r.get("category.name", String.class));
                    if (categoryResponse != null && blogPostDetailResponse.getId() != null) {
                        blogPostDetailResponse.setCategory(categoryResponse);
                    }
                    return blogPostDetailResponse;
                });
      // 详情接口对游客开放（GET permitAll）：未登录时 currentUserId() 会抛 401，
      // 这里用 currentUser() 判空，liked/favorited 不设置（响应 NON_NULL 不序列化）
      Integer currentUserId = SecurityUtils.currentUser() != null ? SecurityUtils.currentUserId() : null;
      var tag=  dsl.select(BLOG_TAGS.ID,BLOG_TAGS.NAME,BLOG_TAGS.CREATED_AT).from(BLOG_POST_TAGS).join(BLOG_TAGS).on(BLOG_POST_TAGS.TAG_ID.eq(BLOG_TAGS.ID)).where(BLOG_POST_TAGS.POST_ID.eq(postId)).fetch(
                r -> r.into(TagResponse.class)
        );
      vo.setTags(tag);

      // likeCount/favCount 是文章的点赞/收藏总次数；liked/favorited 是当前用户是否操作过
      int likeCount = dsl.fetchCount(dsl.selectFrom(BLOG_POST_LIKES).where(BLOG_POST_LIKES.POST_ID.eq(postId)));
      int favoriteCount = dsl.fetchCount(dsl.selectFrom(BLOG_POST_FAVORITES).where(BLOG_POST_FAVORITES.POST_ID.eq(postId)));
      vo.setLikeCount(likeCount);
      vo.setFavCount(favoriteCount);
      if (currentUserId != null) {
          vo.setLiked(dsl.fetchCount(dsl.selectFrom(BLOG_POST_LIKES)
                  .where(BLOG_POST_LIKES.POST_ID.eq(postId).and(BLOG_POST_LIKES.USER_ID.eq(currentUserId)))) > 0);
          vo.setFavorited(dsl.fetchCount(dsl.selectFrom(BLOG_POST_FAVORITES)
                  .where(BLOG_POST_FAVORITES.POST_ID.eq(postId).and(BLOG_POST_FAVORITES.USER_ID.eq(currentUserId)))) > 0);
      }
      return vo;
    }

    public void save(Integer id, PostResponse postResponse, PostRequest postRequest){
        dsl.update(BLOG_POSTS).set(BLOG_POSTS.TITLE, postResponse.getTitle()).
                set(BLOG_POSTS.SUMMARY, postResponse.getSummary()).
                set(BLOG_POSTS.CONTENT_MD, postResponse.getContentMd()).
                // 与正文一起刷新渲染 HTML（请求未带 contentHtml 时为详情页读出的原值，不会误清）
                set(BLOG_POSTS.CONTENT_HTML, postResponse.getContentHtml()).
                set(BLOG_POSTS.COVER_IMAGE, postResponse.getCoverImage()).
                set(BLOG_POSTS.ALLOW_COMMENT, postResponse.getAllowComment()).
                set(BLOG_POSTS.STATUS, postResponse.getStatus()).
                // 重新提交审核时由 service 置 0（待审核），这里随保存一并落库
                set(BLOG_POSTS.REVIEW_STATUS, postResponse.getReviewStatus()).
                set(BLOG_POSTS.READING_TIME, postResponse.getReadingTime()).
                set(BLOG_POSTS.CATEGORY_ID, postRequest.getCategoryId()).where(BLOG_POSTS.ID.eq(id)).execute();
        dsl.deleteFrom(BLOG_POST_TAGS).where(BLOG_POST_TAGS.POST_ID.eq(id)).execute();
        BatchBindStep batch=dsl.batch(dsl.insertInto(BLOG_POST_TAGS, BLOG_POST_TAGS.POST_ID, BLOG_POST_TAGS.TAG_ID).values(id,null));
       for (Integer tagId : postRequest.getTags()){
           batch.bind(id,tagId);
       }
       batch.execute();
    }
    public PageResult findPendingPosts(int page, int pageSize) {
        var table= BlogPosts.BLOG_POSTS;
        SelectConditionStep<BlogPostsRecord> where =null;
        where=dsl.selectFrom(table).where(table.REVIEW_STATUS.eq((short)0).and(table.DELETED_AT.isNull()) );

        List<PostResponse> rows;
        long total;
//        if(where==null){
            rows=dsl.select(table.ID,
                            table.USER_ID,
                            table.TITLE,
                            table.SUMMARY,
                            table.COVER_IMAGE,
                            table.READING_TIME,
                            table.STATUS,
                            table.CREATED_AT,
                            table.UPDATED_AT,
                            BLOG_USERS.NICKNAME.as("authorNickname"),
                            BLOG_USERS.AVATAR.as("authorAvatar"),
                            BLOG_CATEGORIES.ID.as("category.id"),
                            BLOG_CATEGORIES.NAME.as("category.name"))
                    .from(table).join(BLOG_USERS).on(table.USER_ID.eq(BLOG_USERS.ID))
                    .leftJoin(BLOG_CATEGORIES).on(table.CATEGORY_ID.eq(BLOG_CATEGORIES.ID))
                    .where(table.DELETED_AT.isNull()).and(table.REVIEW_STATUS.eq((short)0))
                    .orderBy(table.ID.desc()).
                    limit(pageSize).offset((page - 1) * pageSize).
                    fetchInto(PostResponse.class);
            total = dsl.fetchCount(where);

            rows.forEach(r -> {
                var tag=  dsl.select(BLOG_TAGS.ID,BLOG_TAGS.NAME,BLOG_TAGS.CREATED_AT).from(BLOG_POST_TAGS).join(BLOG_TAGS).on(BLOG_POST_TAGS.TAG_ID.eq(BLOG_TAGS.ID)).where(BLOG_POST_TAGS.POST_ID.eq(r.getId())).fetch(
                        r1 -> r1.into(TagResponse.class)
                );
                r.setTags(tag);
            });
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResult(rows, total, page, pageSize, totalPages);
    }

    public void updateViewCount(Integer id){
        dsl.update(BLOG_POSTS).set(BLOG_POSTS.VIEW_COUNT, BLOG_POSTS.VIEW_COUNT.add(1))
                .where(BLOG_POSTS.ID.eq(id).and(BLOG_POSTS.DELETED_AT.isNull().and(BLOG_POSTS.REVIEW_STATUS.eq((short) 1)))).execute();
    }

    public void deleteByCategoryId(Integer categoryId){
        dsl.deleteFrom(BLOG_POSTS).where(BLOG_POSTS.CATEGORY_ID.eq(categoryId)).execute();
    }
    public void favorite(Integer postId, Integer userId){
        dsl.insertInto(BLOG_POST_FAVORITES).columns(BLOG_POST_FAVORITES.POST_ID, BLOG_POST_FAVORITES.USER_ID).values(postId, userId).execute();
    }

    public int findByFavorite(Integer postId, Integer userId){
        return  dsl.selectFrom(BLOG_POST_FAVORITES).where(BLOG_POST_FAVORITES.POST_ID.eq(postId).and(BLOG_POST_FAVORITES.USER_ID.eq(userId))).fetch().size();
    }

    /** 该文章当前收藏总次数（以收藏明细表为准） */
    public int countFavorites(Integer postId){
        return dsl.fetchCount(dsl.selectFrom(BLOG_POST_FAVORITES).where(BLOG_POST_FAVORITES.POST_ID.eq(postId)));
    }

    /** 取消收藏：删除当前用户对该文章的收藏记录，返回删除行数 */
    public int unfavorite(Integer postId, Integer userId){
        return dsl.deleteFrom(BLOG_POST_FAVORITES)
                .where(BLOG_POST_FAVORITES.POST_ID.eq(postId).and(BLOG_POST_FAVORITES.USER_ID.eq(userId)))
                .execute();
    }

    /** 同步冗余计数列 blog_posts.fav_count（文章列表直接读该列） */
    public void updateFavCount(Integer postId, int favCount){
        dsl.update(BLOG_POSTS).set(BLOG_POSTS.FAV_COUNT, favCount).where(BLOG_POSTS.ID.eq(postId)).execute();
    }

    public boolean findByLike(Integer postId, Integer currentUserId) {
        return dsl.selectFrom(BLOG_POST_LIKES).where(BLOG_POST_LIKES.POST_ID.eq(postId).and(BLOG_POST_LIKES.USER_ID.eq(currentUserId))).fetch().size()>0;
    }

    public void like(Integer postId, Integer currentUserId) {
        dsl.insertInto(BLOG_POST_LIKES).columns(BLOG_POST_LIKES.POST_ID, BLOG_POST_LIKES.USER_ID).values(postId, currentUserId).execute();
    }

    public int countLikes(Integer postId){
        int count=dsl.fetchCount(dsl.selectFrom(BLOG_POST_LIKES).where(BLOG_POST_LIKES.POST_ID.eq(postId)));
        dsl.update(BLOG_POSTS).set(BLOG_POSTS.LIKE_COUNT, count).where(BLOG_POSTS.ID.eq(postId)).execute();
        return count;
    }

    public void unlike(Integer postId, Integer currentUserId) {
        dsl.deleteFrom(BLOG_POST_LIKES)
                .where(BLOG_POST_LIKES.POST_ID.eq(postId).and(BLOG_POST_LIKES.USER_ID.eq(currentUserId)))
                .execute();
    }
}
