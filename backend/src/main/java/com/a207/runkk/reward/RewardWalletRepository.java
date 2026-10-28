package com.a207.runkk.reward;

import java.util.Optional;

/** 리워드 지갑 저장소 포트(설계 6.1.8). 구현은 infra.reward.JpaRewardWalletRepository. reward 밖에서 쓰지 않는다. */
public interface RewardWalletRepository {

    /** 없으면 잔액 0으로 만들고, 행을 잠가(SELECT … FOR UPDATE) 돌려준다(5.3). 트랜잭션 안에서만 부른다. */
    RewardWallet findOrCreateForUpdate(long memberId);

    /** 잠그지 않고 읽는다(조회 연산용, 읽기 전용 트랜잭션은 FOR UPDATE를 쓸 수 없다). 지갑이 없으면 비어 있다. */
    Optional<RewardWallet> findByMember(long memberId);

    void save(RewardWallet wallet);

    void deleteByMember(long memberId);
}
