package com.a207.runkk.infra.reward;

import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardAccrual;
import com.a207.runkk.reward.RewardAccrualRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

/**
 * reward.RewardAccrualRepository의 JPA 구현(설계 5.1.1 infra.reward, D-109). 중복 확인은 유일 색인
 * uq_reward_accrual_source (member_id, reason, source_id), 지난 날짜 합계는
 * idx_reward_accrual_member_accrued (member_id, accrued_at) 범위 조회다(V26).
 */
@Repository
public class JpaRewardAccrualRepository implements RewardAccrualRepository {

    private final EntityManager entityManager;

    public JpaRewardAccrualRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public int amountFor(long memberId, AccrualReason reason, String sourceId) {
        Long sum =
                entityManager
                        .createQuery(
                                "select coalesce(sum(a.amount), 0) from RewardAccrual a"
                                        + " where a.memberId = :m and a.reason = :r"
                                        + " and a.sourceId = :s",
                                Long.class)
                        .setParameter("m", memberId)
                        .setParameter("r", reason)
                        .setParameter("s", sourceId)
                        .getSingleResult();
        return Math.toIntExact(sum);
    }

    @Override
    public int sumOn(long memberId, LocalDate day) {
        Long sum =
                entityManager
                        .createQuery(
                                "select coalesce(sum(a.amount), 0) from RewardAccrual a"
                                        + " where a.memberId = :m and a.accruedAt >= :from"
                                        + " and a.accruedAt < :to and a.reason <> :event",
                                Long.class)
                        .setParameter("m", memberId)
                        .setParameter("event", AccrualReason.EVENT)
                        .setParameter("from", day.atStartOfDay())
                        .setParameter("to", day.plusDays(1).atStartOfDay())
                        .getSingleResult();
        return Math.toIntExact(sum);
    }

    @Override
    public void save(RewardAccrual a) {
        entityManager.persist(a);
    }
}
