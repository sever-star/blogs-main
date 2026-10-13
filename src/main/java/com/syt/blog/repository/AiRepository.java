package com.syt.blog.repository;

import com.syt.blog.jooq.tables.pojos.BlogAiMessages;
import com.syt.blog.jooq.tables.pojos.BlogAiSessions;
import com.syt.blog.jooq.tables.records.BlogAiMessagesRecord;
import com.syt.blog.jooq.tables.records.BlogAiSessionsRecord;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

import static com.syt.blog.jooq.tables.BlogAiMessages.BLOG_AI_MESSAGES;
import static com.syt.blog.jooq.tables.BlogAiSessions.BLOG_AI_SESSIONS;

/**
 * AI 会话与消息的数据访问层（jOOQ）。
 * <p>
 * 会话归属有两种：登录用户按 {@code user_id}，游客按 {@code user_ip}
 * （表结构允许 user_id 为空，正是为此设计）。归属条件由上层组装后传入，
 * 这里只负责拼 SQL，不做鉴权判断。
 */
@Repository
public class AiRepository {

    /** 消息状态：生成中 */
    public static final short STATUS_GENERATING = 0;
    /** 消息状态：已完成 */
    public static final short STATUS_DONE = 1;
    /** 消息状态：失败 */
    public static final short STATUS_FAILED = 2;

    private final DSLContext dsl;

    public AiRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ==================== 会话 ====================

    /** 新建会话，返回自增主键 */
    public long createSession(Integer userId, String userIp, String title) {
        BlogAiSessionsRecord record = dsl.insertInto(BLOG_AI_SESSIONS)
                .set(BLOG_AI_SESSIONS.USER_ID, userId)
                .set(BLOG_AI_SESSIONS.USER_IP, userIp)
                .set(BLOG_AI_SESSIONS.TITLE, title)
                .returning(BLOG_AI_SESSIONS.ID)
                .fetchOne();
        return record.getId();
    }

    /** 按主键查会话，不存在返回 null */
    public BlogAiSessions findSession(long sessionId) {
        return dsl.selectFrom(BLOG_AI_SESSIONS)
                .where(BLOG_AI_SESSIONS.ID.eq(sessionId))
                .fetchOneInto(BlogAiSessions.class);
    }

    /** 按归属条件列会话，最近更新的在前 */
    public List<BlogAiSessions> listSessions(Condition owner, int limit) {
        return dsl.selectFrom(BLOG_AI_SESSIONS)
                .where(owner)
                .orderBy(BLOG_AI_SESSIONS.UPDATED_AT.desc(), BLOG_AI_SESSIONS.ID.desc())
                .limit(limit)
                .fetchInto(BlogAiSessions.class);
    }

    /**
     * 重命名会话。
     * <p>
     * blog_ai_sessions 上有 trg_ai_sessions_updated_at 触发器，UPDATE 时自动刷新
     * updated_at，因此这里不必手动设置。
     */
    public void renameSession(long sessionId, String title) {
        dsl.update(BLOG_AI_SESSIONS)
                .set(BLOG_AI_SESSIONS.TITLE, title)
                .where(BLOG_AI_SESSIONS.ID.eq(sessionId))
                .execute();
    }

    /** 删除会话（消息表有 ON DELETE CASCADE，会一并清理） */
    public void deleteSession(long sessionId) {
        dsl.deleteFrom(BLOG_AI_SESSIONS)
                .where(BLOG_AI_SESSIONS.ID.eq(sessionId))
                .execute();
    }

    /**
     * 触碰会话，让 updated_at 前移（会话列表按「最后活跃时间」排序）。
     * <p>
     * 写入 title = title 的自赋值只为触发 trg_ai_sessions_updated_at，
     * 该触发器会在 UPDATE 时把 updated_at 置为当前时间，因此无需显式赋值。
     */
    public void touchSession(long sessionId) {
        dsl.update(BLOG_AI_SESSIONS)
                .set(BLOG_AI_SESSIONS.TITLE, BLOG_AI_SESSIONS.TITLE)
                .where(BLOG_AI_SESSIONS.ID.eq(sessionId))
                .execute();
    }

    // ==================== 消息 ====================

    /** 追加一条消息，返回自增主键 */
    public long insertMessage(long sessionId, String role, String content, short status) {
        BlogAiMessagesRecord record = dsl.insertInto(BLOG_AI_MESSAGES)
                .set(BLOG_AI_MESSAGES.SESSION_ID, sessionId)
                .set(BLOG_AI_MESSAGES.ROLE, role)
                .set(BLOG_AI_MESSAGES.CONTENT, content)
                .set(BLOG_AI_MESSAGES.STATUS, status)
                .returning(BLOG_AI_MESSAGES.ID)
                .fetchOne();
        return record.getId();
    }

    /** 流式结束后回填助手消息的正文、token 数与最终状态 */
    public void finishMessage(long messageId, String content, int tokens, short status) {
        dsl.update(BLOG_AI_MESSAGES)
                .set(BLOG_AI_MESSAGES.CONTENT, content)
                .set(BLOG_AI_MESSAGES.TOKENS, tokens)
                .set(BLOG_AI_MESSAGES.STATUS, status)
                .where(BLOG_AI_MESSAGES.ID.eq(messageId))
                .execute();
    }

    /** 会话下的全部消息（前端渲染历史，按时间正序） */
    public List<BlogAiMessages> listMessages(long sessionId) {
        return dsl.selectFrom(BLOG_AI_MESSAGES)
                .where(BLOG_AI_MESSAGES.SESSION_ID.eq(sessionId))
                .orderBy(BLOG_AI_MESSAGES.ID.asc())
                .fetchInto(BlogAiMessages.class);
    }

    /**
     * 取最近 N 条已完成消息作为对话上下文（正序返回）。
     * <p>
     * 只取 status=1：生成中/失败的消息内容是残缺的，喂给模型会污染上下文。
     * 先按 id 倒序取 N 条再反转，避免全量加载长会话。
     */
    public List<BlogAiMessages> listRecentContext(long sessionId, int limit) {
        List<BlogAiMessages> recent = dsl.selectFrom(BLOG_AI_MESSAGES)
                .where(BLOG_AI_MESSAGES.SESSION_ID.eq(sessionId))
                .and(BLOG_AI_MESSAGES.STATUS.eq(STATUS_DONE))
                .orderBy(BLOG_AI_MESSAGES.ID.desc())
                .limit(limit)
                .fetchInto(BlogAiMessages.class);
        Collections.reverse(recent);
        return recent;
    }

    /** 会话下消息条数（用于判断是否需要按首条提问自动命名） */
    public int countMessages(long sessionId) {
        return dsl.fetchCount(BLOG_AI_MESSAGES, BLOG_AI_MESSAGES.SESSION_ID.eq(sessionId));
    }
}
