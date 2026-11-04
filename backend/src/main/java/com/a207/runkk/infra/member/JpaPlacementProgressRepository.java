package com.a207.runkk.infra.member;

import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.member.PlacementProgressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * member.PlacementProgressRepository의 JPA 구현(05-21 2.1, 표 placement_progress·placement_round). 행
 * 만들기는 INSERT … ON CONFLICT DO NOTHING 한 번이라 같은 회원의 동시 첫 요청이 겹쳐도 한 행만 생기고, 잠금은
 * PESSIMISTIC_WRITE(SELECT … FOR UPDATE)다.
 */
@Repository
public class JpaPlacementProgressRepository implements PlacementProgressRepository {

    private final EntityManager entityManager;

    public JpaPlacementProgressRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<PlacementProgress> findById(long memberId) {
        return Optional.ofNullable(entityManager.find(PlacementProgress.class, memberId));
    }

    @Override
    public PlacementProgress lockOrCreate(long memberId) {
        entityManager
                .createNativeQuery(
                        "insert into placement_progress (member_id, counted_rounds, version)"
                                + " values (?1, 0, 0) on conflict do nothing")
                .setParameter(1, memberId)
                .executeUpdate();
        return entityManager.find(
                PlacementProgress.class, memberId, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public Optional<PlacementProgress> findByIdForUpdate(long memberId) {
        return Optional.ofNullable(
                entityManager.find(
                        PlacementProgress.class, memberId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public PlacementProgress save(PlacementProgress progress) {
        if (entityManager.contains(progress)) {
            return progress;
        }
        if (entityManager.find(PlacementProgress.class, progress.getMemberId()) == null) {
            entityManager.persist(progress);
            return progress;
        }
        return entityManager.merge(progress);
    }

    @Override
    public void deleteByMemberId(long memberId) {
        PlacementProgress found = entityManager.find(PlacementProgress.class, memberId);
        if (found != null) {
            entityManager.remove(found); // 판 행(placement_round)은 요소 모음이라 함께 지워진다
        }
    }

    @Override
    public boolean existsRound(long memberId, long battleId) {
        return entityManager
                        .createQuery(
                                "select count(r) from PlacementProgress p join p.rounds r"
                                        + " where p.memberId = :m and r.battleId = :b",
                                Long.class)
                        .setParameter("m", memberId)
                        .setParameter("b", battleId)
                        .getSingleResult()
                > 0;
    }
}
