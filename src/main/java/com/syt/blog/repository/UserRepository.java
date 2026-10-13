package com.syt.blog.repository;

import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.BlogUsers;
import com.syt.blog.jooq.tables.records.BlogUsersRecord;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    /**
     * 按主键查用户，不存在返回 null。
     * <p>
     * JWT 认证过滤器每个请求都要取一次用户（拿 role/status，保证降权与禁用立即生效），
     * 这里刻意用 {@code fetchOneInto} 而非 jOOQ 生成的 DAO：前者「查不到返回 null」语义明确，
     * 不必依赖 DAO 对缺失主键的处理方式。
     */
    public com.syt.blog.jooq.tables.pojos.BlogUsers findById(Integer id) {
        if (id == null) {
            return null;
        }
        return dsl.selectFrom(BlogUsers.BLOG_USERS)
                .where(BlogUsers.BLOG_USERS.ID.eq(id))
                .fetchOneInto(com.syt.blog.jooq.tables.pojos.BlogUsers.class);
    }

    /**
     * 分页查用户（管理表格用）：keyword 模糊匹配用户名/昵称/邮箱，按 ID 倒序。
     * <p>
     * page 为 1 基、totalPages 用浮点取整——刻意不复刻 {@code TagRepository.findPaged}
     * 里「page 减 1」「整数除法截断」两处历史问题。
     */
    public PageResult findPaged(int page, int pageSize, String keyword) {
        var table = BlogUsers.BLOG_USERS;
        SelectConditionStep<BlogUsersRecord> where = null;
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim() + "%";
            where = dsl.selectFrom(table).where(
                    table.USERNAME.like(pattern)
                            .or(table.NICKNAME.like(pattern))
                            .or(table.EMAIL.like(pattern)));
        }

        List<com.syt.blog.jooq.tables.pojos.BlogUsers> rows;
        long total;
        if (where == null) {
            rows = dsl.selectFrom(table).orderBy(table.ID.desc())
                    .limit(pageSize).offset((page - 1) * pageSize)
                    .fetchInto(com.syt.blog.jooq.tables.pojos.BlogUsers.class);
            total = dsl.fetchCount(table);
        } else {
            rows = where.orderBy(table.ID.desc())
                    .limit(pageSize).offset((page - 1) * pageSize)
                    .fetchInto(com.syt.blog.jooq.tables.pojos.BlogUsers.class);
            total = dsl.fetchCount(where);
        }
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResult(rows, total, page, pageSize, totalPages);
    }

    /** 统计「启用中的管理员」数量，用于防止把最后一个管理员降级或禁用 */
    public long countActiveAdmins() {
        var table = BlogUsers.BLOG_USERS;
        return dsl.fetchCount(dsl.selectFrom(table)
                .where(table.ROLE.eq("ADMIN").and(table.STATUS.eq((short) 1))));
    }
}
