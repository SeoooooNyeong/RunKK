package com.a207.runkk.infra.runbody;

import com.a207.runkk.runbody.RunBodyJob;
import com.a207.runkk.runbody.RunBodyJobRepository;
import com.a207.runkk.runbody.RunBodyStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** RunBodyJobRepository의 JPA 구현(05-19 D-441). aiprivacy의 JpaAiPurgeRequestRepository와 같은 방식이다. */
@Repository
public class JpaRunBodyJobRepository implements RunBodyJobRepository {

    private static final List<RunBodyStatus> OPEN =
            List.of(RunBodyStatus.PENDING, RunBodyStatus.ANALYZING);

    private final EntityManager em;

    public JpaRunBodyJobRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<RunBodyJob> find(String recordId) {
        return Optional.ofNullable(em.find(RunBodyJob.class, recordId));
    }

    @Override
    public void save(RunBodyJob job) {
        if (!em.contains(job)) {
            em.merge(job);
        }
    }

    @Override
    public List<String> dueRecordIds(LocalDateTime now, int limit) {
        return em.createQuery(
                        "select j.recordId from RunBodyJob j where j.status in :open and"
                                + " j.nextPollAt <= :now order by j.nextPollAt",
                        String.class)
                .setParameter("open", OPEN)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public void delete(String recordId) {
        em.createQuery("delete from RunBodyJob j where j.recordId = :id")
                .setParameter("id", recordId)
                .executeUpdate();
    }

    @Override
    public void deleteByMember(long memberId) {
        em.createQuery("delete from RunBodyJob j where j.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
