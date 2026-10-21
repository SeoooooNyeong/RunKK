package com.a207.runkk.battle;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 대결 판정 사건(설계 6.1.6, D-31). battle이 발행하고 reward.RewardService가 커밋 뒤 받는다.
 *
 * @param battleId 대결 ID
 * @param type 대결 종류
 * @param judgement 참가자별 판정, 정정 여부
 * @param at 판정 시각
 */
public record BattleJudged(long battleId, BattleType type, Judgement judgement, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
