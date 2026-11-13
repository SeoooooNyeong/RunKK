package com.a207.runkk.infra.aicoach;

import com.a207.runkk.aicoach.CoachConversation;
import com.a207.runkk.aicoach.CoachDailyUsage;
import com.a207.runkk.aicoach.CoachRejected;
import com.a207.runkk.aicoach.CoachStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaCoachStore implements CoachStore {
    private final EntityManager em;

    public JpaCoachStore(EntityManager em) {
        this.em = em;
    }

    @Override
    public Lease claim(long memberId, int limit, LocalDateTime now) {
        em.createNativeQuery(
                        """
                        INSERT INTO coach_daily_usage (member_id, usage_day, answered_count)
                        VALUES (:member, :day, 0) ON CONFLICT DO NOTHING
                        """)
                .setParameter("member", memberId)
                .setParameter("day", now.toLocalDate())
                .executeUpdate();
        CoachDailyUsage usage = lock(memberId);
        if (usage == null || usage.reservedAt(now)) {
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        }
        if (!usage.hasRoom(now.toLocalDate(), limit)) {
            throw new CoachRejected(CoachRejected.Reason.COACH_QUOTA_EXCEEDED);
        }
        Lease lease = new Lease(memberId, UUID.randomUUID().toString());
        usage.reserve(lease, now);
        return lease;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConversation(long memberId, String id, LocalDateTime cutoff) {
        return !em.createQuery(
                        """
                        SELECT c.conversationId FROM CoachConversation c
                        WHERE c.memberId = :member AND c.conversationId = :id AND c.lastAskedAt > :cutoff
                        """,
                        String.class)
                .setParameter("member", memberId)
                .setParameter("id", id)
                .setParameter("cutoff", cutoff)
                .getResultList()
                .isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> conversations(long memberId, LocalDateTime cutoff) {
        return em
                .createQuery(
                        """
                        SELECT c FROM CoachConversation c WHERE c.memberId = :member AND c.lastAskedAt > :cutoff
                        ORDER BY c.lastAskedAt DESC, c.conversationId ASC
                        """,
                        CoachConversation.class)
                .setParameter("member", memberId)
                .setParameter("cutoff", cutoff)
                .getResultList()
                .stream()
                .map(CoachConversation::view)
                .toList();
    }

    @Override
    public boolean started(
            Lease lease, String id, String preview, boolean continuing, LocalDateTime at) {
        if (!validLease(lease, at)) {
            return false;
        }
        CoachConversation conversation = em.find(CoachConversation.class, id);
        if (!continuing && conversation == null) {
            em.persist(new CoachConversation(id, lease.memberId(), preview, at));
            return true;
        }
        if (continuing && conversation != null && conversation.ownedBy(lease.memberId())) {
            conversation.touch(at);
            return true;
        }
        return false;
    }

    @Override
    public boolean answered(Lease lease, LocalDateTime at) {
        CoachDailyUsage usage = lock(lease.memberId());
        if (usage == null || !usage.owns(lease) || !usage.reservedAt(at)) {
            return false;
        }
        usage.answered();
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Lease lease) {
        CoachDailyUsage usage = lock(lease.memberId());
        if (usage != null && usage.owns(lease)) {
            usage.release();
        }
    }

    @Override
    public int purge(LocalDateTime cutoff) {
        return em.createQuery("DELETE FROM CoachConversation c WHERE c.lastAskedAt <= :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    @Override
    public void deleteMember(long memberId) {
        // 탈퇴 회원 상태를 먼저 쓰고, 접수와 같은 회원 → 사용량 행 잠금 순서를 따른다.
        em.flush();
        em.createQuery("DELETE FROM CoachDailyUsage u WHERE u.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
        em.createQuery("DELETE FROM CoachConversation c WHERE c.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }

    private CoachDailyUsage lock(long memberId) {
        return em.find(CoachDailyUsage.class, memberId, LockModeType.PESSIMISTIC_WRITE);
    }

    private boolean validLease(Lease lease, LocalDateTime at) {
        CoachDailyUsage usage = lock(lease.memberId());
        return usage != null && usage.owns(lease) && usage.reservedAt(at);
    }
}
