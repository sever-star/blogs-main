package com.syt.blog.repository;

import com.syt.blog.jooq.tables.BlogAnnouncements;
import com.syt.blog.jooq.tables.BlogNotifications;
import com.syt.blog.jooq.tables.records.BlogNotificationsRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 站内通知数据访问。直接操作已建好的 blog_notifications 表，不扩展表结构：
 * actor（触发者）信息由 NotificationService 拼进 content 文本，不单独存列。
 */
@Repository
public class NotificationRepository {
    private final DSLContext dsl;
    private static final BlogNotifications T = BlogNotifications.BLOG_NOTIFICATIONS;
    private static final BlogAnnouncements A = BlogAnnouncements.BLOG_ANNOUNCEMENTS;
    public NotificationRepository(DSLContext dsl, AnnouncementsRepository announcementsRepository) {
        this.dsl = dsl;
    }

    /** 分页查询某用户的通知，可只看未读，按创建时间倒序 */
    public List<BlogNotificationsRecord> findPaged(Integer userId, int page, int pageSize, boolean onlyUnread) {
        var cond = T.USER_ID.eq(userId);
        if (onlyUnread) {
            cond = cond.and(T.IS_READ.eq((short) 0));
        }
        return dsl.selectFrom(T)
                .where(cond)
                .orderBy(T.CREATED_AT.desc())
                .limit((page - 1) * pageSize, pageSize)
                .fetch();
    }

    public long count(Integer userId, boolean onlyUnread) {
        var cond = T.USER_ID.eq(userId);
        if (onlyUnread) {
            cond = cond.and(T.IS_READ.eq((short) 0));
        }
        return dsl.fetchCount(dsl.selectFrom(T).where(cond));
    }

    public Map<String,Long> countUnread(Integer userId) {
        // 不能用 fetchMap(T.TYPE, Long.class)：该重载会把 (type, count) 两列整行
        // 映射成 Long，直接抛 "No DefaultRecordMapper strategy applies" → 未读数接口 500。
        // 取出行后手动组装 key→count。
        var rows = dsl.select(T.TYPE, DSL.count().as("cnt")).from(T)
                .where(T.USER_ID.eq(userId).and(T.IS_READ.eq((short) 0)))
                .groupBy(T.TYPE)
                .fetch();
        Map<String, Long> result = new java.util.HashMap<>();
        for (var row : rows) {
            result.put(row.get(T.TYPE), row.get("cnt", Long.class));
        }
        return result;
    }

    public void insert(Integer userId, String type, String title, String content, String link) {
        dsl.insertInto(T)
                .set(T.USER_ID, userId)
                .set(T.TYPE, type)
                .set(T.TITLE, title)
                .set(T.CONTENT, content)
                .set(T.LINK, link)
                .set(T.IS_READ, (short) 0)
                .set(T.CREATED_AT, LocalDateTime.now())
                .execute();
    }

    /** 标记单条已读，带 user_id 校验防越权。返回受影响行数。 */
    public int markRead(Long id, Integer userId) {
        return dsl.update(T)
                .set(T.IS_READ, (short) 1)
                .where(T.ID.eq(id).and(T.USER_ID.eq(userId)))
                .execute();
    }

    public int markAllRead(Integer userId) {
        return dsl.update(T)
                .set(T.IS_READ, (short) 1)
                .where(T.USER_ID.eq(userId).and(T.IS_READ.eq((short) 0)))
                .execute();
    }

    public void Insert(Integer userId) {
        List<com.syt.blog.jooq.tables.pojos.BlogAnnouncements> announcements = dsl.selectFrom(A)
                .whereNotExists(dsl.selectOne().from(T).where(T.SOURCE_ID.in(A.ID).and(T.USER_ID.eq(userId)).and(T.TYPE.eq("announcement")))).fetchInto(com.syt.blog.jooq.tables.pojos.BlogAnnouncements.class);
       announcements.forEach(announcement->{
           dsl.insertInto(T)
                   .set(T.USER_ID, userId)
                   .set(T.TYPE, "announcement")
                   .set(T.TITLE, announcement.getTitle())
                   .set(T.CONTENT, announcement.getContent())
                   .set(T.LINK, "/announcements")
                   .set(T.IS_READ, (short) 0)
                   .set(T.CREATED_AT, LocalDateTime.now())
                   .set(T.SOURCE_ID, announcement.getId())
                   .execute();
       });
    }

    public com.syt.blog.jooq.tables.pojos.BlogNotifications findById(Long id) {
        return dsl.selectFrom(T).where(T.ID.eq(id)).fetchOneInto(com.syt.blog.jooq.tables.pojos.BlogNotifications.class);
    }

    public void delete(Long id) {
        dsl.delete(T).where(T.ID.eq(id)).execute();
    }
}
