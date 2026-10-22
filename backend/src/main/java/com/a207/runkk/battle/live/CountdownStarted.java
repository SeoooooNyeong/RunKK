package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.common.DisplayName;
import java.time.LocalDateTime;

/**
 * 종료 카운트다운 신호(설계 5.7.2, FR-107).
 *
 * @param battleId 대결 ID
 * @param arrivedName 먼저 도착한 쪽 표시명
 * @param endsAt 카운트다운 종료 시각
 */
public record CountdownStarted(long battleId, DisplayName arrivedName, LocalDateTime endsAt)
        implements BattleMessage {

    @Override
    public String type() {
        return "CountdownStarted";
    }
}
