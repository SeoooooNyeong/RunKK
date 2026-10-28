package com.a207.runkk.infra.chat;

import com.a207.runkk.crew.chat.ChatCursor;
import com.a207.runkk.crew.chat.CrewChatMessage;
import com.a207.runkk.crew.chat.CrewChatMessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * crew.chat.CrewChatMessageRepository의 JPA 구현(설계 6.1.13 infra.chat, D-101). crew_chat_message 표만 읽고
 * 쓴다. 쪽 조회는 메시지 ID 커서이고 (crew_id, id DESC) 색인 하나가 before·after·최신 세 갈래를 받친다(6.2 A-25, OFFSET 없음).
 */
@Repository
public class JpaCrewChatMessageRepository implements CrewChatMessageRepository {

    private static final String VISIBLE =
            "select m from CrewChatMessage m where m.crewId = :crew"
                    + " and m.deleted = false and m.hidden = false";

    private final EntityManager entityManager;

    public JpaCrewChatMessageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 새 메시지는 바로 넣는다(IDENTITY). 고유 색인 위반은 DataIntegrityViolationException으로 바뀌어 올라간다. */
    @Override
    public CrewChatMessage save(CrewChatMessage message) {
        if (message.id() == null) {
            entityManager.persist(message);
            return message;
        }
        return entityManager.contains(message) ? message : entityManager.merge(message);
    }

    @Override
    public Optional<CrewChatMessage> findById(long id) {
        return Optional.ofNullable(entityManager.find(CrewChatMessage.class, id));
    }

    /** 문맥에 이미 있는 엔티티는 떼어 내고 잠금과 함께 다시 읽는다(잠금 전 상태로 덮어쓰지 않게, JpaCrewPostRepository와 같다). */
    @Override
    public Optional<CrewChatMessage> findByIdForUpdate(long id) {
        CrewChatMessage cached = entityManager.find(CrewChatMessage.class, id);
        if (cached == null) {
            return Optional.empty();
        }
        entityManager.detach(cached);
        return Optional.ofNullable(
                entityManager.find(CrewChatMessage.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<CrewChatMessage> findByClientMessageId(long authorId, String clientMessageId) {
        return entityManager
                .createQuery(
                        "select m from CrewChatMessage m where m.authorId = :author"
                                + " and m.clientMessageId = :client",
                        CrewChatMessage.class)
                .setParameter("author", authorId)
                .setParameter("client", clientMessageId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<CrewChatMessage> findPage(long crewId, ChatCursor cursor) {
        TypedQuery<CrewChatMessage> query;
        if (cursor.after() != null) {
            query =
                    entityManager
                            .createQuery(
                                    VISIBLE + " and m.id > :after order by m.id asc",
                                    CrewChatMessage.class)
                            .setParameter("after", cursor.after());
        } else if (cursor.before() != null) {
            query =
                    entityManager
                            .createQuery(
                                    VISIBLE + " and m.id < :before order by m.id desc",
                                    CrewChatMessage.class)
                            .setParameter("before", cursor.before());
        } else {
            query =
                    entityManager.createQuery(
                            VISIBLE + " order by m.id desc", CrewChatMessage.class);
        }
        return query.setParameter("crew", crewId).setMaxResults(cursor.size() + 1).getResultList();
    }

    @Override
    public void anonymizeAuthor(long memberId) {
        entityManager
                .createQuery(
                        "update CrewChatMessage m set m.authorId = null where m.authorId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
