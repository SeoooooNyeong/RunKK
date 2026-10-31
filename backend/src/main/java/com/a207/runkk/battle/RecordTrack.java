package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.Objects;

/**
 * 워치가 미리 받아 두는 기록 대결 상대 하나(v0.21 D-377, FR-260, 앱 domain.battle.RecordTrack과 같은 뜻). 대결을 만들지 않고 기록
 * 대결 시작 정보(RecordBattleStart)와 같은 방법으로 만든 상대 표를 싣는다.
 *
 * @param type SELF 또는 CELEBRITY
 * @param celebrityId 유명인 기록 ID, 나와의 싸움이면 null
 * @param opponentName 상대 표시명(나와의 싸움 "MM/dd의 나", 유명인 가림 이름)
 * @param distance 대결 거리
 * @param finishTime 상대 기록 완주 시간
 * @param track 상대 경과 시간·누적 거리 표(대결 거리에서 자름)
 * @param source 유명인 출처, 나와의 싸움이면 null
 * @param estimated 구간 기록 없이 균등 페이스로 만든 표인가(A-006)
 * @param figureKey 유명인 캐릭터 그림 키, 없거나 나와의 싸움이면 null
 */
public record RecordTrack(
        BattleType type,
        Long celebrityId,
        DisplayName opponentName,
        Distance distance,
        Duration finishTime,
        OpponentTrack track,
        Source source,
        boolean estimated,
        String figureKey) {

    public RecordTrack {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(opponentName, "opponentName");
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(finishTime, "finishTime");
        Objects.requireNonNull(track, "track");
    }
}
