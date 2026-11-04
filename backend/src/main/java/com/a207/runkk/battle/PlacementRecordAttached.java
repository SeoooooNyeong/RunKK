package com.a207.runkk.battle;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 배치고사 대결에 기록이 붙었는데 이번에 판정이 나지 않았다는 사건(05-21 D-512). BattleResultService.applyRecord가 발행하고 battle
 * 안의 PlacementRoundListener만 커밋 뒤 받아 바로 센다.
 *
 * @param battleId 대결 ID
 * @param memberId 기록을 올린 회원
 * @param at 붙은 시각
 */
public record PlacementRecordAttached(long battleId, long memberId, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
