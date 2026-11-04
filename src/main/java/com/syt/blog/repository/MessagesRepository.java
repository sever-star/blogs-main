package com.syt.blog.repository;

import com.syt.blog.DTO.Request.MessageRequest;
import com.syt.blog.DTO.Response.MessageResponse;
import com.syt.blog.DTO.Response.MessagesResponse;
import com.syt.blog.DTO.Response.MessagesResponse.PeerUser;

import static com.syt.blog.jooq.Tables.BLOG_CONVERSATIONS;
import static com.syt.blog.jooq.Tables.BLOG_NOTIFICATIONS;
import static com.syt.blog.jooq.tables.BlogMessages.BLOG_MESSAGES;
import static com.syt.blog.jooq.tables.BlogUsers.BLOG_USERS;
import static org.jooq.impl.DSL.*;


import com.syt.blog.common.BusinessException;
import com.syt.blog.jooq.tables.BlogConversations;
import com.syt.blog.jooq.tables.records.BlogConversationsRecord;
import com.syt.blog.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 私信数据访问。操作已建好的 blog_conversations / blog_messages 两表。
 *
 * 前置条件：
 *  1) 两表建好后需重新构建后端，触发 jOOQ 代码生成（生成 BlogConversations / BlogMessages 表类）。
 *  2) MessagesResponse 需带 Lombok @Data（提供 setter），peerUser 用 record 的全参构造组装。
 *
 * 设计：会话按「用户对」聚合，user_a_id < user_b_id 固定，当前用户可能落在 a 或 b 任一侧，
 *      故对方 id / 未读数 / hidden 过滤均用 CASE WHEN user_a_id = :uid 分支映射。
 *      last_message_id 是冗余列（不设外键），用 LEFT JOIN 取最后一条消息正文，空会话得到 NULL。
 */
@Repository
@RequiredArgsConstructor
public class MessagesRepository {
    private final DSLContext dsl;

    /**
     * 当前用户的会话列表（仅未隐藏），按最后消息时间倒序，空会话排末尾。
     * 嵌套 peerUser 由手动组装，规避 jOOQ 默认映射器对嵌套结构的限制。
     */
    public List<MessagesResponse> findConversations(Integer currentUserId, Integer page, Integer pageSize,Integer peerUserId) {
        // 当前用户在 a 侧时对方是 b，否则对方是 a
        Field<Integer> peerId = when(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId), BLOG_CONVERSATIONS.USER_B_ID)
                .otherwise(BLOG_CONVERSATIONS.USER_A_ID);
        // 未读数按当前用户身份映射
        Field<Integer> unreadCount = when(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId), BLOG_CONVERSATIONS.A_UNREAD_COUNT)
                .otherwise(BLOG_CONVERSATIONS.B_UNREAD_COUNT);
        // 最后一条消息正文预览：超过 50 字符截断加 …；content 为 NULL（空会话）时 charLength 为 NULL，
        // 条件不成立走 otherwise 返回 NULL，与接口约定一致
        Field<String> preview = when(charLength(BLOG_MESSAGES.CONTENT).gt(50), left(BLOG_MESSAGES.CONTENT, 50).concat("…"))
                .otherwise(BLOG_MESSAGES.CONTENT);
        Condition condition;
        if(peerUserId!=null){
            Integer UserAId=currentUserId<peerUserId?currentUserId:peerUserId;
            Integer UserBId=currentUserId<peerUserId?peerUserId:currentUserId;
            condition=BLOG_CONVERSATIONS.USER_A_ID.eq(UserAId).and(BLOG_CONVERSATIONS.USER_B_ID.eq(UserBId));
        }else {
            condition=BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId).and(BLOG_CONVERSATIONS.A_HIDDEN.eq((short) 0))
                    .or(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId).and(BLOG_CONVERSATIONS.B_HIDDEN.eq((short) 0)));
        }
        var rows = dsl.select(
                        BLOG_CONVERSATIONS.ID.as("id"),
                        peerId.as("peer_id"),
                        BLOG_USERS.USERNAME.as("peer_username"),
                        BLOG_USERS.NICKNAME.as("peer_nickname"),
                        BLOG_USERS.AVATAR.as("peer_avatar"),
                        preview.as("last_message_preview"),
                        BLOG_CONVERSATIONS.LAST_MESSAGE_AT.as("last_message_at"),
                        unreadCount.as("unread_count"),
                        BLOG_CONVERSATIONS.CREATED_AT.as("created_at"),
                        BLOG_CONVERSATIONS.UPDATED_AT.as("updated_at")
                )
                .from(BLOG_CONVERSATIONS)
                .join(BLOG_USERS).on(BLOG_USERS.ID.eq(peerId))
                .leftJoin(BLOG_MESSAGES).on(BLOG_MESSAGES.ID.eq(BLOG_CONVERSATIONS.LAST_MESSAGE_ID))
                .where(condition)
                .orderBy(BLOG_CONVERSATIONS.LAST_MESSAGE_AT.desc().nullsLast(),
                        BLOG_CONVERSATIONS.UPDATED_AT.desc())
                .limit((page - 1) * pageSize, pageSize)
                .fetch();

        List<MessagesResponse> list = new ArrayList<>(rows.size());
        for (Record r : rows) {
            MessagesResponse resp = new MessagesResponse();
            resp.setId(r.get("id", Long.class));
            resp.setPeerUser(new PeerUser(
                    r.get("peer_id", Integer.class),
                    r.get("peer_username", String.class),
                    r.get("peer_nickname", String.class),
                    r.get("peer_avatar", String.class)
            ));
            resp.setLastMessagePreview(r.get("last_message_preview", String.class));
            resp.setLastMessageAt(r.get("last_message_at", java.time.LocalDateTime.class));
            resp.setUnreadCount(r.get("unread_count", Integer.class));
            resp.setCreatedAt(r.get("created_at", java.time.LocalDateTime.class));
            resp.setUpdatedAt(r.get("updated_at", java.time.LocalDateTime.class));
            list.add(resp);
        }
        return list;
    }

    /** 当前用户参与且未隐藏的会话总数，配合 findConversations 组装 PageResult。 */
    public long countConversations(Integer userId) {
        return dsl.fetchCount(dsl.selectFrom(BLOG_CONVERSATIONS)
                .where(BLOG_CONVERSATIONS.USER_A_ID.eq(userId).and(BLOG_CONVERSATIONS.A_HIDDEN.eq((short) 0))
                        .or(BLOG_CONVERSATIONS.USER_B_ID.eq(userId).and(BLOG_CONVERSATIONS.B_HIDDEN.eq((short) 0)))));
    }

    @Transactional
    public MessagesResponse findMessage(Integer peerUserId ,Integer currentUserId) {
        createMessage(peerUserId,currentUserId);
        List<MessagesResponse> list= findConversations(currentUserId,1,10,peerUserId);
        if (list.isEmpty()){
            throw new BusinessException(500,"会话初始化失败");
        }
        return list.get(0);
    }
    public Long createMessage(Integer peerUserId ,Integer currentUserId) {
        Integer UserAId=peerUserId<currentUserId?peerUserId:currentUserId;
        Integer UserBId=peerUserId<currentUserId?currentUserId:peerUserId;
        Condition condition;
        condition=BLOG_CONVERSATIONS.USER_A_ID.eq(UserAId).and(BLOG_CONVERSATIONS.USER_B_ID.eq(UserBId));
        boolean exists= dsl.fetchExists(dsl.selectFrom(BLOG_CONVERSATIONS).where(condition));
        Long id;
        if(!exists){
            id= dsl.insertInto(BLOG_CONVERSATIONS).columns(BLOG_CONVERSATIONS.USER_A_ID,BLOG_CONVERSATIONS.USER_B_ID).values(UserAId,UserBId)
                    .onConflict(BLOG_CONVERSATIONS.USER_A_ID,BLOG_CONVERSATIONS.USER_B_ID).doNothing().returningResult(BLOG_CONVERSATIONS.ID).fetchOne(BLOG_CONVERSATIONS.ID,Long.class);
            return id;
        }
        id= dsl.update(BLOG_CONVERSATIONS).set(BLOG_CONVERSATIONS.A_HIDDEN,(short)0).set(BLOG_CONVERSATIONS.B_HIDDEN,(short)0).where(condition).returningResult(BLOG_CONVERSATIONS.ID).fetchOne(BLOG_CONVERSATIONS.ID,Long.class);
        return id;
    }

    public List<MessageResponse> findList(Long conversationId, Integer currentUserId, Integer page, Integer pageSize) {
        Condition condition=BLOG_MESSAGES.CONVERSATION_ID.eq(conversationId).and(BLOG_MESSAGES.SENDER_ID.eq(currentUserId).or(BLOG_MESSAGES.RECEIVER_ID.eq(currentUserId)));
        List<MessageResponse> list= dsl.select(BLOG_MESSAGES.ID,
                BLOG_MESSAGES.CONVERSATION_ID,
                BLOG_MESSAGES.SENDER_ID,
                BLOG_MESSAGES.RECEIVER_ID,
                BLOG_USERS.NICKNAME.as("senderNickname"),
                BLOG_USERS.AVATAR.as("senderAvatar"),
                BLOG_MESSAGES.CONTENT,
                BLOG_MESSAGES.IS_READ,
                BLOG_MESSAGES.READ_AT,
                BLOG_MESSAGES.CREATED_AT).from(BLOG_MESSAGES).join(BLOG_USERS).on(BLOG_MESSAGES.SENDER_ID.eq(BLOG_USERS.ID))
                .where(condition)
                .orderBy(BLOG_MESSAGES.CREATED_AT.desc(),BLOG_MESSAGES.RECEIVER_ID.desc())
                .limit((page - 1) * pageSize, pageSize)
                .fetchInto(MessageResponse.class);
        return list;
    }

    @Transactional
    public MessageResponse addMessage(Integer currentUserId, @Valid MessageRequest messageRequest) {
        Integer receiverId=messageRequest.getReceiverId();
        Integer UserAId=receiverId<currentUserId?receiverId:currentUserId;
        Integer UserBId=receiverId<currentUserId?currentUserId:receiverId;
        Condition condition;
        condition=BLOG_CONVERSATIONS.USER_A_ID.eq(UserAId).and(BLOG_CONVERSATIONS.USER_B_ID.eq(UserBId));
        Long id= createMessage(receiverId,currentUserId);
        Long messageId= dsl.insertInto(BLOG_MESSAGES).columns(BLOG_MESSAGES.CONVERSATION_ID,BLOG_MESSAGES.SENDER_ID,BLOG_MESSAGES.RECEIVER_ID,BLOG_MESSAGES.CONTENT,BLOG_MESSAGES.IS_READ)
                .values(id,currentUserId,receiverId,messageRequest.getContent(),(short)0).returningResult(BLOG_MESSAGES.ID).fetchOne(BLOG_MESSAGES.ID,Long.class);
        if(UserAId.equals(receiverId)){
            dsl.update(BLOG_CONVERSATIONS).set(BLOG_CONVERSATIONS.LAST_MESSAGE_ID,messageId)
                    .set(BLOG_CONVERSATIONS.LAST_MESSAGE_AT, LocalDateTime.now())
                    .set(BLOG_CONVERSATIONS.A_HIDDEN,(short)0)
                    .set(BLOG_CONVERSATIONS.A_UNREAD_COUNT,BLOG_CONVERSATIONS.A_UNREAD_COUNT.add(1)).where(condition).execute();
        }else if(UserBId.equals(receiverId)){
            dsl.update(BLOG_CONVERSATIONS).set(BLOG_CONVERSATIONS.LAST_MESSAGE_ID,messageId)
                    .set(BLOG_CONVERSATIONS.LAST_MESSAGE_AT, LocalDateTime.now())
                    .set(BLOG_CONVERSATIONS.B_HIDDEN,(short)0)
                    .set(BLOG_CONVERSATIONS.B_UNREAD_COUNT,BLOG_CONVERSATIONS.B_UNREAD_COUNT.add(1)).where(condition).execute();
        }
        MessageResponse messageResponse= dsl.select(BLOG_MESSAGES.ID,
                BLOG_MESSAGES.CONVERSATION_ID,
                BLOG_MESSAGES.SENDER_ID,
                BLOG_MESSAGES.RECEIVER_ID,
                BLOG_USERS.NICKNAME.as("senderNickname"),
                BLOG_USERS.AVATAR.as("senderAvatar"),
                BLOG_MESSAGES.CONTENT,
                BLOG_MESSAGES.IS_READ,
                BLOG_MESSAGES.READ_AT,
                BLOG_MESSAGES.CREATED_AT).from(BLOG_MESSAGES).join(BLOG_USERS).on(BLOG_MESSAGES.SENDER_ID.eq(BLOG_USERS.ID))
                .where(BLOG_MESSAGES.ID.eq(messageId))
                .orderBy(BLOG_MESSAGES.CREATED_AT.desc(),BLOG_MESSAGES.RECEIVER_ID.desc())
                .fetchOneInto(MessageResponse.class);
        if (messageResponse == null){
            throw new BusinessException(500,"私信发送失败");
        }

        return messageResponse;

    }

    public Integer messageCount(Long conversationId,Integer currentUserId) {
        return dsl.fetchCount(dsl.selectFrom(BLOG_MESSAGES).where(BLOG_MESSAGES.CONVERSATION_ID.eq(conversationId).and(BLOG_MESSAGES.SENDER_ID.eq(currentUserId).or(BLOG_MESSAGES.RECEIVER_ID.eq(currentUserId)))));
    }

    @Transactional
    public Integer updateRead(Integer currentUserId, Long conversationId) {
       dsl.update(BLOG_MESSAGES).set(BLOG_MESSAGES.IS_READ,(short)1)
               .set(BLOG_MESSAGES.READ_AT,LocalDateTime.now())
               .where(BLOG_MESSAGES.CONVERSATION_ID.eq(conversationId).and(BLOG_MESSAGES.RECEIVER_ID.eq(currentUserId)
                       .and(BLOG_MESSAGES.IS_READ.eq((short)0)))).execute();

       int affected =  dsl.update(BLOG_CONVERSATIONS)
       .set(BLOG_CONVERSATIONS.B_UNREAD_COUNT,
                when(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId),  0)
                        .otherwise(BLOG_CONVERSATIONS.B_UNREAD_COUNT))
       .set(BLOG_CONVERSATIONS.A_UNREAD_COUNT,
                when(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId),  0)
                        .otherwise(BLOG_CONVERSATIONS.A_UNREAD_COUNT))
       .where(BLOG_CONVERSATIONS.ID.eq(conversationId)
                .and(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId)
                        .or(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId))))
       .execute();
       if (affected == 0) {
            throw new BusinessException(404, "会话不存在或非参与者");
       }
       return 0;
    }

    public void deleteMessage(Integer currentUserId, Long conversationId) {
        int affected = dsl.update(BLOG_CONVERSATIONS)
                .set(BLOG_CONVERSATIONS.A_HIDDEN,
                        when(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId), (short) 1)
                                .otherwise(BLOG_CONVERSATIONS.A_HIDDEN))
                .set(BLOG_CONVERSATIONS.B_HIDDEN,
                        when(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId), (short) 1)
                                .otherwise(BLOG_CONVERSATIONS.B_HIDDEN))
                .where(BLOG_CONVERSATIONS.ID.eq(conversationId)
                        .and(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId)
                                .or(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId))))
                .execute();

        if (affected == 0) {
            throw new BusinessException(404, "会话不存在或非参与者");
        }

    }

    public Integer countUnread(Integer currentUserId) {
        Integer a= dsl.select(sum(BLOG_CONVERSATIONS.A_UNREAD_COUNT)).from(BLOG_CONVERSATIONS)
                .where(BLOG_CONVERSATIONS.USER_A_ID.eq(currentUserId)).fetchOne(0, Integer.class);
        Integer b= dsl.select(sum(BLOG_CONVERSATIONS.B_UNREAD_COUNT)).from(BLOG_CONVERSATIONS)
                .where(BLOG_CONVERSATIONS.USER_B_ID.eq(currentUserId)).fetchOne(0, Integer.class);
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }
}
