package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.FriendInvite;
import com.a207.runkk.battle.FriendInviteRepository;
import com.a207.runkk.battle.FriendInviteStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * battle.FriendInviteRepository의 JPA 구현(설계 5.6.2, 표 friend_invite, V30). 대기 초대는 회원당 하나라는 규칙의 최종 보장은
 * 부분 고유 인덱스 uq_friend_invite_pending이고, 위반은 저장소 예외 변환으로 DataIntegrityViolationException이 된다.
 */
@Repository
public class JpaFriendInviteRepository implements FriendInviteRepository {

    private final EntityManager entityManager;

    public JpaFriendInviteRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public FriendInvite save(FriendInvite invite) {
        if (invite.getId() == null) {
            entityManager.persist(invite);
            return invite;
        }
        return entityManager.contains(invite) ? invite : entityManager.merge(invite);
    }

    @Override
    public FriendInvite saveImmediately(FriendInvite invite) {
        FriendInvite saved = save(invite);
        entityManager.flush();
        return saved;
    }

    @Override
    public Optional<FriendInvite> findById(long id) {
        return Optional.ofNullable(entityManager.find(FriendInvite.class, id));
    }

    @Override
    public Optional<FriendInvite> findByToken(String token) {
        return entityManager
                .createQuery(
                        "select i from FriendInvite i where i.token = :token", FriendInvite.class)
                .setParameter("token", token)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<FriendInvite> findPending(long inviterId) {
        return entityManager
                .createQuery(
                        "select i from FriendInvite i where i.inviterId = :inviter"
                                + " and i.status = :pending order by i.id desc",
                        FriendInvite.class)
                .setParameter("inviter", inviterId)
                .setParameter("pending", FriendInviteStatus.PENDING)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsOpen(long inviterId, LocalDateTime now) {
        return !entityManager
                .createQuery(
                        "select i.id from FriendInvite i where i.inviterId = :inviter"
                                + " and i.status = :pending and i.expiresAt > :now",
                        Long.class)
                .setParameter("inviter", inviterId)
                .setParameter("pending", FriendInviteStatus.PENDING)
                .setParameter("now", now)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public List<FriendInvite> findDuePending(LocalDateTime now, int limit) {
        return entityManager
                .createQuery(
                        "select i from FriendInvite i where i.status = :pending"
                                + " and i.expiresAt <= :now order by i.expiresAt, i.id",
                        FriendInvite.class)
                .setParameter("pending", FriendInviteStatus.PENDING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }
}
