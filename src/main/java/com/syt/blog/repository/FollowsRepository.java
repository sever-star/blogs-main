package com.syt.blog.repository;


import com.syt.blog.jooq.tables.BlogFollows;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.syt.blog.jooq.tables.BlogFollows.BLOG_FOLLOWS;


@Repository
@RequiredArgsConstructor
public class FollowsRepository {
    private final DSLContext dsl;
    private final BlogFollows F=BLOG_FOLLOWS;
    public void addFollows(Integer followerId, Integer followeeId) {
        dsl.insertInto(F).columns(F.FOLLOWER_ID,F.FOLLOWEE_ID).values(followerId,followeeId)
                .onConflict(F.FOLLOWER_ID, F.FOLLOWEE_ID).doNothing().execute();
    }

    public void deleteFollows(Integer followerId, Integer followeeId) {
        dsl.deleteFrom(F)
                .where(F.FOLLOWER_ID.eq(followerId).and(F.FOLLOWEE_ID.eq(followeeId)))
                .execute();
    }

    /** 当前用户是否已关注 followee：用 fetchExists 判定，避免 fetchOneInto(表定义类) 在行存在时映射失败 */
    public boolean getFollowStatus(Integer followerId, Integer followeeId) {
        return dsl.fetchExists(
                dsl.selectFrom(F)
                        .where(F.FOLLOWER_ID.eq(followerId).and(F.FOLLOWEE_ID.eq(followeeId))));
    }

    public List<Integer> getFollow(Integer followerId) {
        return dsl.select(F.FOLLOWEE_ID).from(F).where(F.FOLLOWER_ID.eq(followerId)).fetchInto(Integer.class);
    }
}
