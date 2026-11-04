package com.a207.runkk.infra.aiprivacy;

import com.a207.runkk.aiprivacy.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAiPurgeRequestRepository implements AiPurgeRequestRepository {
    private final EntityManager em;

    public JpaAiPurgeRequestRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<AiPurgeRequest> findForUpdate(long memberId) {
        return Optional.ofNullable(
                em.find(AiPurgeRequest.class, memberId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public void save(AiPurgeRequest request) {
        if (!em.contains(request)) em.merge(request);
    }

    @Override
    public List<Long> dueMembers(LocalDateTime now) {
        return em.createQuery(
                        "select r.memberId from AiPurgeRequest r where (r.status <> :done or"
                                + " r.retentionCheckDone = false) and r.nextAt <= :now and"
                                + " (r.leaseUntil is null or r.leaseUntil <= :now) order by"
                                + " r.requestedAt",
                        Long.class)
                .setParameter("done", PurgeStatus.DONE)
                .setParameter("now", now)
                .getResultList();
    }
}
