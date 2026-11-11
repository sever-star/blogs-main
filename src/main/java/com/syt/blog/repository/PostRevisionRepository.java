package com.syt.blog.repository;

import com.syt.blog.DTO.Request.PostRequest;
import com.syt.blog.DTO.Response.PostResponse;
import com.syt.blog.DTO.Response.PostRevisionResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;

import static com.syt.blog.jooq.Tables.BLOG_POSTS;
import static com.syt.blog.jooq.tables.BlogPostRevisions.BLOG_POST_REVISIONS;
import static com.syt.blog.jooq.tables.BlogUsers.BLOG_USERS;

import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostRevisionRepository {
    private final DSLContext dsl;
    private final Integer KEEP=10;
    public List<PostRevisionResponse> findRevisions(Long postId, Integer currentUserId) {
        List<PostRevisionResponse> rows= dsl.select(BLOG_POST_REVISIONS.ID,
                BLOG_POST_REVISIONS.VERSION_NO,
                BLOG_POST_REVISIONS.EDITED_BY,
                BLOG_POST_REVISIONS.EDITED_AT,
                BLOG_POST_REVISIONS.CHANGE_NOTE,
                BLOG_USERS.USERNAME.as("editedByName")).
                from(BLOG_POST_REVISIONS).join(BLOG_USERS).on(BLOG_USERS.ID.eq(BLOG_POST_REVISIONS.EDITED_BY))
                .where(BLOG_POST_REVISIONS.POST_ID.eq(Math.toIntExact(postId)))
                .orderBy(BLOG_POST_REVISIONS.VERSION_NO.desc())
                .fetchInto(PostRevisionResponse.class);
        return rows;
    }

    public PostRevisionResponse findOneRevision(Long postId, Long revId) {
        return  dsl.select(BLOG_POST_REVISIONS.ID,
                BLOG_POST_REVISIONS.VERSION_NO,
                BLOG_POST_REVISIONS.POST_ID,
                BLOG_POST_REVISIONS.TITLE,
                BLOG_POST_REVISIONS.CONTENT_MD,
                BLOG_POST_REVISIONS.CONTENT_HTML,
                BLOG_POST_REVISIONS.SUMMARY,
                BLOG_POST_REVISIONS.COVER_IMAGE,
                BLOG_POST_REVISIONS.CATEGORY_ID,
                BLOG_POST_REVISIONS.EDITED_BY,
                BLOG_POST_REVISIONS.EDITED_AT,
                BLOG_POST_REVISIONS.CHANGE_NOTE,
                BLOG_USERS.USERNAME.as("editedByName")
                        ).from(BLOG_POST_REVISIONS)
                .join(BLOG_USERS).on(BLOG_USERS.ID.eq(BLOG_POST_REVISIONS.EDITED_BY))
                .where(BLOG_POST_REVISIONS.POST_ID.eq(Math.toIntExact(postId)).and(BLOG_POST_REVISIONS.ID.eq(revId)))
                .fetchOneInto(PostRevisionResponse.class);
    }
    /**
     * 存储文章版本
     */
       public void snapshotIfChanged(PostResponse post,Integer currentUserId) {
        Integer count= dsl.fetchCount(dsl.selectFrom(BLOG_POST_REVISIONS)
                .where(BLOG_POST_REVISIONS.CONTENT_MD.eq(post.getContentMd()).and(BLOG_POST_REVISIONS.POST_ID.eq(post.getId()))));
        if(count==0){
            Integer revcount= dsl.fetchCount(dsl.selectFrom(BLOG_POST_REVISIONS).where(BLOG_POST_REVISIONS.POST_ID.eq(post.getId())));
            if (revcount>=KEEP) {
                Long id= dsl.select(BLOG_POST_REVISIONS.ID).from(BLOG_POST_REVISIONS)
                        .where(BLOG_POST_REVISIONS.POST_ID.eq(post.getId()))
                        .orderBy(BLOG_POST_REVISIONS.VERSION_NO.asc())
                        .limit(1).fetchAnyInto(Long.class);
                dsl.deleteFrom(BLOG_POST_REVISIONS).where(BLOG_POST_REVISIONS.ID.eq(id)).execute();
            }
            //获取版本号
            Integer versionNo= dsl.select(DSL.coalesce(DSL.max(BLOG_POST_REVISIONS.VERSION_NO), 0).add(1))
                    .from(BLOG_POST_REVISIONS).where(BLOG_POST_REVISIONS.POST_ID.eq(Math.toIntExact(post.getId()))).fetchOneInto(Integer.class);

            dsl.insertInto(BLOG_POST_REVISIONS).columns(BLOG_POST_REVISIONS.TITLE,
                            BLOG_POST_REVISIONS.CONTENT_MD,
                            BLOG_POST_REVISIONS.CONTENT_HTML,
                            BLOG_POST_REVISIONS.SUMMARY,
                            BLOG_POST_REVISIONS.COVER_IMAGE,
                            BLOG_POST_REVISIONS.CATEGORY_ID,
                            BLOG_POST_REVISIONS.POST_ID,
                            BLOG_POST_REVISIONS.EDITED_BY,
                            BLOG_POST_REVISIONS.EDITED_AT,
                            BLOG_POST_REVISIONS.VERSION_NO)
                    .values(post.getTitle(),
                            post.getContentMd(),
                            post.getContentHtml(),
                            post.getSummary(),
                            post.getCoverImage(),
                            post.getCategory().getId(),
                            post.getId(),
                            currentUserId,LocalDateTime.now(),versionNo).execute();
        }
    }
    public PostRequest addRevision(PostResponse post, Long revId, Integer currentUserId) {
        snapshotIfChanged(post,currentUserId);
        PostRequest postRequest= dsl.selectFrom(BLOG_POST_REVISIONS)
                .where(BLOG_POST_REVISIONS.ID.eq(revId)).fetchOneInto(PostRequest.class);
        return postRequest;

    }
    /**
     * 把 revision 的正文快照写回 blog_posts（专用回滚写库）。
     * 只更新 title/content/封面/分类与审核状态，不碰 blog_post_tags——
     * 方案约定 tags 不进版本快照，回滚时保留当前标签不变。
     */
    public void applyRevisionToPost(Integer postId, PostRequest revision, Short status, Short reviewStatus) {
        dsl.update(BLOG_POSTS)
                .set(BLOG_POSTS.TITLE, revision.getTitle())
                .set(BLOG_POSTS.CONTENT_MD, revision.getContentMd())
                .set(BLOG_POSTS.CONTENT_HTML, revision.getContentHtml())
                .set(BLOG_POSTS.SUMMARY, revision.getSummary())
                .set(BLOG_POSTS.COVER_IMAGE, revision.getCoverImage())
                .set(BLOG_POSTS.CATEGORY_ID, revision.getCategoryId())
                .set(BLOG_POSTS.STATUS, status)
                .set(BLOG_POSTS.REVIEW_STATUS, reviewStatus)
                .where(BLOG_POSTS.ID.eq(postId))
                .execute();
    }

}
