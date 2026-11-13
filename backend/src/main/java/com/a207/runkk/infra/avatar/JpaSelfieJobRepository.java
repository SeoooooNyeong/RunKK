package com.a207.runkk.infra.avatar;

import com.a207.runkk.avatar.SelfieJob;
import com.a207.runkk.avatar.SelfieJobRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSelfieJobRepository implements SelfieJobRepository {
    private final EntityManager em;

    public JpaSelfieJobRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<SelfieJob> find(String requestId) {
        return Optional.ofNullable(em.find(SelfieJob.class, requestId));
    }

    @Override
    public Optional<SelfieJob> byJob(long memberId, String jobId) {
        return em
                .createQuery(
                        "select j from SelfieJob j where j.memberId = :member and j.jobId = :job",
                        SelfieJob.class)
                .setParameter("member", memberId)
                .setParameter("job", jobId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<SelfieJob> pending(long memberId) {
        return em
                .createQuery(
                        "select j from SelfieJob j where j.memberId = :member and j.finished ="
                                + " false",
                        SelfieJob.class)
                .setParameter("member", memberId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public void save(SelfieJob job) {
        if (!em.contains(job)) em.merge(job);
    }

    @Override
    public void deleteByMember(long memberId) {
        em.createQuery("delete from SelfieJob j where j.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
