package com.a207.runkk.reward;

import java.time.LocalDate;

/**
 * 적립 내역 저장소 포트(설계 6.1.8, D-109). 지갑을 읽을 때 전체 내역을 읽지 않도록 중복 확인과 지난 날짜 합계만 색인으로 답한다. 구현은
 * infra.reward.JpaRewardAccrualRepository. reward 밖에서 쓰지 않는다.
 */
public interface RewardAccrualRepository {

    /** 근거 하나의 적립량(유일 색인 uq_reward_accrual_source). 없으면 0. */
    int amountFor(long memberId, AccrualReason reason, String sourceId);

    /** 그날(사건 발생일) 적립 합계(색인 idx_reward_accrual_member_accrued). 보정 배치의 지난 날짜만 부른다. */
    int sumOn(long memberId, LocalDate day);

    void save(RewardAccrual a);
}
