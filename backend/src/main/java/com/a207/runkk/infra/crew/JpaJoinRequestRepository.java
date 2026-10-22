package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.JoinRequest;
import com.a207.runkk.crew.JoinRequestRepository;
import com.a207.runkk.crew.JoinRequestStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** crew.JoinRequestRepository의 JPA 구현(설계 5.1.1 infra.crew). join_request만 읽고 쓴다. */
@Repository
public class JpaJoinRequestRepository implements JoinRequestRepository {

    private final EntityManager entityManager;

    public JpaJoinRequestRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public JoinRequest save(JoinRequest request) {
        if (request.getId() == null) {
            entityManager.persist(request);
            return request;
        }
        return entityManager.contains(request) ? request : entityManager.merge(request);
    }

    @Override
    public Optional<JoinRequest> findById(long id) {
        return Optional.ofNullable(entityManager.find(JoinRequest.class, id));
    }

    @Override
    public boolean existsPending(long crewId, long memberId) {
        return entityManager
                        .createQuery(
                                "select count(r) from JoinRequest r where r.crewId = :crew"
                                        + " and r.memberId = :member and r.status = :pending",
                                Long.class)
                        .setParameter("crew", crewId)
                        .setParameter("member", memberId)
                        .setParameter("pending", JoinRequestStatus.PENDING)
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<JoinRequest> findPending(long crewId) {
        return entityManager
                .createQuery(
                        "select r from JoinRequest r where r.crewId = :crew and r.status = :pending"
                                + " order by r.requestedAt, r.id",
                        JoinRequest.class)
                .setParameter("crew", crewId)
                .setParameter("pending", JoinRequestStatus.PENDING)
                .getResultList();
    }

    @Override
    public List<JoinRequest> findPendingByMember(long memberId) {
        return entityManager
                .createQuery(
                        "select r from JoinRequest r where r.memberId = :member and r.status ="
                                + " :pending order by r.requestedAt, r.id",
                        JoinRequest.class)
                .setParameter("member", memberId)
                .setParameter("pending", JoinRequestStatus.PENDING)
                .getResultList();
    }

    @Override
    public List<JoinRequest> findPendingOlderThan(LocalDateTime cutoff) {
        return entityManager
                .createQuery(
                        "select r from JoinRequest r where r.status = :pending"
                                + " and r.requestedAt <= :cutoff order by r.id",
                        JoinRequest.class)
                .setParameter("pending", JoinRequestStatus.PENDING)
                .setParameter("cutoff", cutoff)
                .getResultList();
    }

    @Override
    public void deleteByCrew(long crewId) {
        entityManager
                .createQuery("delete from JoinRequest r where r.crewId = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from JoinRequest r where r.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
