package com.syt.blog.repository;

import com.syt.blog.DTO.Response.BlogPostDetailResponse;
import com.syt.blog.DTO.Response.CommentsResponse;
import com.syt.blog.jooq.tables.BlogCommentLikes;
import com.syt.blog.jooq.tables.BlogComments;
import com.syt.blog.jooq.tables.records.BlogCommentsRecord;
import org.hibernate.annotations.Comments;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class PostCommentRepository {
    private final DSLContext dsl;

    public PostCommentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }
    //查询文章下的所有评论
    public List<BlogCommentsRecord> findApprovedByPostId(Integer postId) {
        return dsl.selectFrom(BlogComments.BLOG_COMMENTS).where(BlogComments.BLOG_COMMENTS.POST_ID.eq(postId)).fetch();
    }

    //根据评论id列表查询评论的点赞数
    public Map<Long,Integer> countLikesByCommentIds(List<Long> commentIds) {
        var table=BlogCommentLikes.BLOG_COMMENT_LIKES;
        return dsl.select(table.COMMENT_ID,DSL.count().as("cnt"))
                .from(table)
                .where(table.COMMENT_ID.in(commentIds))
                .groupBy(table.COMMENT_ID)
                .fetchMap(r->r.get(table.COMMENT_ID),
                        r->r.get(DSL.field("cnt",Integer.class)));
    }
    //查询用户点赞的评论id
    /**
     * 为什么使用set？因为set可以自动去重，而list不能自动去重
     * set使用contains方法判断是否存在,时时间复杂度一直为O(1)
     * */
    public Set<Long> findLikedCommentIds(List<Long> commentIds, Integer userId) {
        var table=BlogCommentLikes.BLOG_COMMENT_LIKES;
        return dsl.select(table.COMMENT_ID)
                .from(table)
                .where(table.COMMENT_ID.in(commentIds)).and(table.USER_ID.eq(userId))
                .fetchSet(table.COMMENT_ID);
    }

    public void save(com.syt.blog.jooq.tables.pojos.BlogComments comment) {
        LocalDateTime now = LocalDateTime.now();
        BlogCommentsRecord saved = dsl.insertInto(BlogComments.BLOG_COMMENTS)
                .set(BlogComments.BLOG_COMMENTS.NICKNAME,comment.getNickname())
                .set(BlogComments.BLOG_COMMENTS.AVATAR,comment.getAvatar())
                .set(BlogComments.BLOG_COMMENTS.CONTENT,comment.getContent())
                .set(BlogComments.BLOG_COMMENTS.USER_ID,comment.getUserId())
                .set(BlogComments.BLOG_COMMENTS.POST_ID,comment.getPostId())
                .set(BlogComments.BLOG_COMMENTS.PARENT_ID,comment.getParentId())
                .set(BlogComments.BLOG_COMMENTS.REPLY_TO_ID,comment.getReplyToId())
                .set(BlogComments.BLOG_COMMENTS.IS_ADMIN,comment.getIsAdmin())
                .set(BlogComments.BLOG_COMMENTS.CREATED_AT, now)
                .returning()
                .fetchOne();
        // 把自增 id 与创建时间回写到 POJO：POST 评论的响应直接用它映射，
        // 不回写的话前端拿到 id=null（回复该评论时 parentId 为空会退化成顶级评论）、
        // createdAt=null（发布时间显示 1970/Invalid Date）
        if (saved != null) {
            comment.setId(saved.getId());
        }
        comment.setCreatedAt(now);
    }

    public BlogCommentsRecord getCommentById(Integer commentId) {
        return dsl.selectFrom(BlogComments.BLOG_COMMENTS).where(BlogComments.BLOG_COMMENTS.ID.eq(Long.valueOf(commentId))).fetchOne();
    }


    @Transactional
    public void deleteComments(Integer commentId) {
        List<Long> commentIds = dsl.select(BlogComments.BLOG_COMMENTS.ID)
                .from(BlogComments.BLOG_COMMENTS)
                .where(BlogComments.BLOG_COMMENTS.PARENT_ID.eq(Long.valueOf(commentId))
                        .or(BlogComments.BLOG_COMMENTS.REPLY_TO_ID.eq(Long.valueOf(commentId)))
                        .or(BlogComments.BLOG_COMMENTS.ID.eq(Long.valueOf(commentId)))).fetch(BlogComments.BLOG_COMMENTS.ID);
        dsl.deleteFrom(BlogCommentLikes.BLOG_COMMENT_LIKES)
                .where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.in(commentIds)).execute();
        dsl.deleteFrom(BlogComments.BLOG_COMMENTS)
                .where(BlogComments.BLOG_COMMENTS.ID.in(commentIds)).execute();
    }

    @Transactional
    public Integer saveLike(Long commentId, Integer userId) {
        Record record =  dsl.selectFrom(BlogCommentLikes.BLOG_COMMENT_LIKES)
                .where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.eq(commentId))
                .and(BlogCommentLikes.BLOG_COMMENT_LIKES.USER_ID.eq(userId)).fetchOne();
        if (record!=null) {
            throw new IllegalArgumentException("您已点赞该评论");
        }
        dsl.insertInto(BlogCommentLikes.BLOG_COMMENT_LIKES)
                .set( BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID,commentId)
                .set(BlogCommentLikes.BLOG_COMMENT_LIKES.USER_ID,userId)
                .set(BlogCommentLikes.BLOG_COMMENT_LIKES.CREATED_AT, LocalDateTime.now())
                .execute();
        Integer likeCount= dsl.fetchCount(dsl.selectFrom(BlogCommentLikes.BLOG_COMMENT_LIKES).where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.eq(commentId)));
        return likeCount;
    }

    public Integer deleteLike(Long aLong, Integer userId) {
        Record record = dsl.selectFrom(BlogCommentLikes.BLOG_COMMENT_LIKES)
                .where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.eq(aLong))
                .and(BlogCommentLikes.BLOG_COMMENT_LIKES.USER_ID.eq(userId)).fetchOne();
        // record 存在说明已点赞，正是可取消的情形；不存在才报错。
        // 原判断写反（record!=null 时抛错），导致取消点赞永远失败。
        if (record == null) {
            throw new IllegalArgumentException("您未点赞该评论");
        }
        dsl.deleteFrom(BlogCommentLikes.BLOG_COMMENT_LIKES)
                .where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.eq(aLong)).and(BlogCommentLikes.BLOG_COMMENT_LIKES.USER_ID.eq(userId)).execute();
        Integer likeCount= dsl.fetchCount(dsl.selectFrom(BlogCommentLikes.BLOG_COMMENT_LIKES).where(BlogCommentLikes.BLOG_COMMENT_LIKES.COMMENT_ID.eq(aLong)));
        return likeCount;
    }
}
