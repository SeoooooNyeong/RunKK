package com.a207.runkk.infra.reward;

import com.a207.runkk.reward.RewardWallet;
import com.a207.runkk.reward.RewardWalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * reward.RewardWalletRepository의 JPA 구현(설계 5.1.1 infra.reward).
 * reward_wallet·reward_accrual·owned_item 테이블만 읽고 쓴다(D-32). 지갑 행 잠금은 "없으면 넣기(INSERT … ON CONFLICT
 * DO NOTHING) → SELECT … FOR UPDATE" 순서로 해 첫 적립이 동시에 와도 지갑이 하나다(5.3).
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
                        "insert into reward_wallet (member_id, balance, version) values (:m, 0, 0)"
                                + " on conflict do nothing")
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
        RewardWallet wallet = entityManager.find(RewardWallet.class, memberId);
        if (wallet != null) {
            entityManager.remove(wallet);
        }
    }
}
