package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import java.util.Objects;

/**
 * 기록 대결 생성 입력(설계 6.1.6 Battle.againstRecord, A-07).
 *
 * @param type GHOST, SELF, CELEBRITY
 * @param memberId 러너
 * @param distance 대결 거리
 * @param opponent 기록 상대 값
 * @param track 상대 표(대결 거리에서 자름). 앱이 보간한다(D-05)
 * @param opponentValues 비교표 상대 값. 기록이 지워져도 결과 화면이 남도록 대결에 함께 남긴다(FR-172)
 */
public record RecordBattleSpec(
        BattleType type,
        long memberId,
        Distance distance,
        OpponentRef opponent,
        OpponentTrack track,
        ComparisonValues opponentValues) {

    public RecordBattleSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(opponentValues, "opponentValues");
        if (!type.isAgainstRecord()) { // RANDOM·FRIEND는 실시간 상대라 기록 대결이 아니다(D-225)
            throw new IllegalArgumentException("기록 대결 종류가 아니다: " + type);
        }
    }

    /**
     * 상대 기록에서 만든다. GhostMatcher는 상대가 다른 러너 기록인지 가상 기록인지 묻지 않는다(13.1 예). 상대 표가 대결 거리에 닿지 않으면
     * IllegalArgumentException.
     */
    public static RecordBattleSpec of(
            BattleType type, long memberId, Distance distance, OpponentRecord record) {
        OpponentTrack track = record.track(distance);
        OpponentRef ref =
                new OpponentRef(
                        record.kind(),
                        record.recordId(),
                        record.displayName(),
                        record.displayTier(),
                        track.arrivalTime(distance)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "상대 기록이 대결 거리에 닿지 않는다")));
        return new RecordBattleSpec(
                type, memberId, distance, ref, track, record.comparisonValues(distance));
    }
}
