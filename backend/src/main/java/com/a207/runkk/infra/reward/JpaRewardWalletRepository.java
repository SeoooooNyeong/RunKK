package com.a207.runkk.infra.reward;

import com.a207.runkk.reward.RewardWallet;
import com.a207.runkk.reward.RewardWalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * reward.RewardWalletRepository의 JPA 구현(설계 5.1.1 infra.reward). reward_wallet·owned_item 테이블을 읽고 쓰고
 * 탈퇴 때 reward_accrual도 지운다(D-32). 적립 내역은 JpaRewardAccrualRepository가 맡는다(D-109). 지갑 행 잠금은 "없으면
 * 넣기(INSERT … ON CONFLICT DO NOTHING) → SELECT … FOR UPDATE" 순서로 해 첫 적립이 동시에 와도 지갑이 하나다(5.3).
 */
@Repository
public class JpaRewardWalletRepository implements RewardWalletRepository {

    private final EntityManager entityManager;

    public JpaRewardWalletRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public RewardWallet findOrCreateForUpdate(long memberId) {
        entityManager
                .createNativeQuery(
                        "insert into reward_wallet (member_id, balance, last_accrual_date_amount,"
                                + " version) values (:m, 0, 0, 0) on conflict do nothing")
                .setParameter("m", memberId)
                .executeUpdate();
        return entityManager.find(RewardWallet.class, memberId, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public Optional<RewardWallet> findByMember(long memberId) {
        return Optional.ofNullable(entityManager.find(RewardWallet.class, memberId));
    }

    @Override
    public void save(RewardWallet wallet) {
        if (!entityManager.contains(wallet)) {
            entityManager.merge(wallet);
        }
    }

    @Override
    public void deleteByMember(long memberId) {
        // 적립 내역은 지갑 컬렉션이 아니므로(D-109) 먼저 지운다. PostgreSQL은 FK ON DELETE CASCADE도 지운다
        entityManager
                .createQuery("delete from RewardAccrual a where a.memberId = :m")
                .setParameter("m", memberId)
                .executeUpdate();
        RewardWallet wallet = entityManager.find(RewardWallet.class, memberId);
        if (wallet != null) {
            entityManager.remove(wallet);
        }
    }
}
