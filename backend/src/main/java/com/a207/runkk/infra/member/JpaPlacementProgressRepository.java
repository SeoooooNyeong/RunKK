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
 *
 * <p>요소 모음(rounds)이 있는 행의 잠금 조회는 Hibernate가 잠금 없이 행·판을 한 문장으로 읽은 뒤 {@code select … where member_id
 * = ? and version = ? for update}로 잠근다(follow-on 잠금). 잠금을 기다리는 사이 다른 셈이 커밋하면 version이 달라 낙관적 잠금
 * 충돌(TransientDataAccessException)이 되고 PlacementRoundListener가 다시 읽어 센다. 그래서 잠근 뒤 옛 판 목록으로 세는 일은
 * 없다(코드 리뷰 PL1-B2를 실제 DB 동시 시험 TC-5923으로 확인, 재현되지 않음).
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
