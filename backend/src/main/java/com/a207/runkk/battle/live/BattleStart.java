package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 대결 시작 신호(설계 5.7.2, FR-087). 기록 상대면 상대 표를 실어 앱이 보간한다(D-05). 랜덤이면 opponentTrack이 null이다.
 *
 * @param battleId 대결 ID
 * @param startAt 출발 시각
 * @param opponentTrack 상대 경과 시간·누적 거리 표, 랜덤이면 null
 */
public record BattleStart(long battleId, LocalDateTime startAt, List<TrackPoint> opponentTrack)
        implements BattleMessage {

    public BattleStart {
        opponentTrack = opponentTrack == null ? null : List.copyOf(opponentTrack);
    }

    @Override
    public String type() {
        return "BattleStart";
    }
}
