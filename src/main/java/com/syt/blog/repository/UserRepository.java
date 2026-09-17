package com.syt.blog.repository;

import com.syt.blog.jooq.tables.BlogUsers;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final DSLContext dsl;


    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public com.syt.blog.jooq.tables.pojos.BlogUsers findByUsername(String username) {
        return dsl.selectFrom(BlogUsers.BLOG_USERS)
                .where(BlogUsers.BLOG_USERS.USERNAME.eq(username))
                .fetchOneInto(com.syt.blog.jooq.tables.pojos.BlogUsers.class);
    }
}
