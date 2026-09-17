package com.syt.blog.repository;

import com.syt.blog.jooq.tables.pojos.BlogRefreshTokens;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

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
}
