package com.syt.blog.repository;

import com.syt.blog.jooq.tables.pojos.BlogRefreshTokens;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.syt.blog.jooq.Tables.BLOG_REFRESH_TOKENS;

@Repository
public class RefreshTokenRepository {
   private final DSLContext dsl;

    public RefreshTokenRepository(DSLContext dsl) {
        this.dsl = dsl;
    }
    public BlogRefreshTokens findByToken(String token) {
        return dsl.selectFrom(BLOG_REFRESH_TOKENS)
                .where(BLOG_REFRESH_TOKENS.TOKEN.eq(token))
                .fetchOneInto(BlogRefreshTokens.class);
    }

    public void revokeAllByUserId(Integer userId) {
        dsl.update(BLOG_REFRESH_TOKENS)
                .set(BLOG_REFRESH_TOKENS.REVOKED, 1)
                .set(BLOG_REFRESH_TOKENS.UPDATED_AT, LocalDateTime.now())
                .where(BLOG_REFRESH_TOKENS.USER_ID.eq(userId))
                .and(BLOG_REFRESH_TOKENS.REVOKED.eq(0))  // 只动还没撤的
                .execute();
    }
}
