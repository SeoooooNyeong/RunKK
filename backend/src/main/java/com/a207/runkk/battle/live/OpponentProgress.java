package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.Duration;

/**
 * 상대 진행도(설계 5.7.2, FR-088). 위도·경도·코스가 없다(FR-089, NFR-021).
 *
 * @param battleId 대결 ID
 * @param opponentName 상대 표시명
 * @param opponentDistance 상대 누적 거리(미터)
 * @param opponentElapsed 상대 경과 시간(초)
 * @param gapMeters 받는 러너 기준 거리 차(앞서면 양수)
 * @param connection 상대 연결 상태
 */
public record OpponentProgress(
        long battleId,
        DisplayName opponentName,
        Distance opponentDistance,
        Duration opponentElapsed,
        long gapMeters,
        Connection connection)
        implements BattleMessage {

    @Override
    public String type() {
        return "OpponentProgress";
    }

    /** 상대 연결 상태. CHECKING = 진행값이 3초 넘게 오지 않아 연결 확인 중(FR-090). */
    public enum Connection {
        CONNECTED,
        CHECKING
    }
}
