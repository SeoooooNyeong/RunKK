package com.a207.runkk.battle;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;
import java.util.Optional;

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

    /** 승자 쪽(무승부·판정 불가면 비어 있다). reward가 Judgement를 import하지 않게 넘겨준다(D-134, 13.1 규칙 11). */
    public Optional<Side> winnerSide() {
        return judgement.winnerSide();
    }

    /** 판정 자체의 시각(Judgement.at). 정정 사건이면 사건 시각 at과 다를 수 있다. */
    public LocalDateTime judgedAt() {
        return judgement.at();
    }
}
