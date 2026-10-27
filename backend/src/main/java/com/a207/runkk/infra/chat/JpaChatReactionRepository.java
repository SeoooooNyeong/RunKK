package com.a207.runkk.infra.chat;

import com.a207.runkk.crew.chat.ChatReaction;
import com.a207.runkk.crew.chat.ChatReactionRepository;
import com.a207.runkk.crew.chat.ReactionKind;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * crew.chat.ChatReactionRepository의 JPA 구현(설계 6.1.13 infra.chat, FR-210). crew_chat_reaction 표만 읽고
 * 쓴다. 달기는 복합 기본 키 (message_id, member_id, kind)에 INSERT … ON CONFLICT DO NOTHING 한 번이라 같은 리액션이 동시에
 * 와도 한 행이고 트랜잭션이 깨지지 않는다(5.3).
 */
@Repository
public class JpaChatReactionRepository implements ChatReactionRepository {

    private final EntityManager entityManager;

    public JpaChatReactionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean add(ChatReaction r) {
        return entityManager
                        .createNativeQuery(
                                "insert into crew_chat_reaction (message_id, member_id, kind,"
                                        + " reacted_at) values (:message, :member, :kind, :at)"
                                        + " on conflict do nothing")
                        .setParameter("message", r.messageId())
                        .setParameter("member", r.memberId())
                        .setParameter("kind", r.kind().name())
                        .setParameter("at", r.reactedAt())
                        .executeUpdate()
                > 0;
    }

    @Override
    public boolean remove(long messageId, long memberId, ReactionKind kind) {
        return entityManager
                        .createQuery(
                                "delete from ChatReaction r where r.messageId = :message"
                                        + " and r.memberId = :member and r.kind = :kind")
                        .setParameter("message", messageId)
                        .setParameter("member", memberId)
                        .setParameter("kind", kind)
                        .executeUpdate()
                > 0;
    }

    @Override
    public Map<Long, Map<ReactionKind, Integer>> countsOf(Collection<Long> messageIds) {
        Map<Long, Map<ReactionKind, Integer>> counts = new HashMap<>();
        if (messageIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select r.messageId, r.kind, count(r) from ChatReaction r"
                                        + " where r.messageId in :ids group by r.messageId, r.kind",
                                Object[].class)
                        .setParameter("ids", messageIds)
                        .getResultList();
        for (Object[] row : rows) {
            counts.computeIfAbsent((Long) row[0], id -> new EnumMap<>(ReactionKind.class))
                    .put((ReactionKind) row[1], ((Number) row[2]).intValue());
        }
        return counts;
    }

    @Override
    public Map<Long, Set<ReactionKind>> mineOf(long memberId, Collection<Long> messageIds) {
        Map<Long, Set<ReactionKind>> mine = new HashMap<>();
        if (messageIds.isEmpty()) {
            return mine;
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select r.messageId, r.kind from ChatReaction r"
                                        + " where r.memberId = :member and r.messageId in :ids",
                                Object[].class)
                        .setParameter("member", memberId)
                        .setParameter("ids", messageIds)
                        .getResultList();
        for (Object[] row : rows) {
            mine.computeIfAbsent((Long) row[0], id -> EnumSet.noneOf(ReactionKind.class))
                    .add((ReactionKind) row[1]);
        }
        return mine;
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from ChatReaction r where r.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
