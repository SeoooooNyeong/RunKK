package com.a207.runkk.member;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 배치고사 판 한 번을 셀 때 battle이 넘기는 값(05-21 2.1, battle → member 입력). member는 battle·running을 모른다.
 *
 * @param battleId 대결 ID
 * @param score 그 판 러닝 기록 점수
 * @param battleDistance 고른 대결 거리
 * @param runDistance 러닝 기록 거리
 * @param runElapsed 러닝 기록 운동 시간
 * @param averagePace 평균 페이스, 없으면 null
 * @param verdict 판 승패
 * @param validRunOrdinal 그 기록의 유효 러닝 순번(1 이상)
 * @param countedAt 센 시각
 */
public record PlacementRoundInput(
        long battleId,
        double score,
        StandardDistance battleDistance,
        Distance runDistance,
        Duration runElapsed,
        Pace averagePace,
        PlacementVerdict verdict,
        int validRunOrdinal,
        LocalDateTime countedAt) {

    public PlacementRoundInput {
        Objects.requireNonNull(battleDistance, "battleDistance");
        Objects.requireNonNull(runDistance, "runDistance");
        Objects.requireNonNull(runElapsed, "runElapsed");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(countedAt, "countedAt");
        if (validRunOrdinal < 1) {
            throw new IllegalArgumentException("유효 러닝 순번은 1 이상: " + validRunOrdinal);
        }
    }
}
