package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.RunComparison;
import java.time.Duration;
import java.util.Optional;

/**
 * 비교표의 상대 값(설계 6.1.6 OpponentRecord.comparisonValues). 공개되지 않은 항목은 null이고 화면에 "공개 안 됨"으로
 * 보인다(FR-093).
 *
 * @param elapsed 기록(대결 거리 도달 시간)
 * @param averagePace 평균 페이스
 * @param fastestKm 가장 빠른 1km
 * @param overtakes 추월 횟수(랜덤 대결 상대만)
 * @param maxLead 상대가 가장 앞섰을 때 거리 차
 */
public record ComparisonValues(
        Duration elapsed, Pace averagePace, Pace fastestKm, Integer overtakes, Distance maxLead) {

    public static ComparisonValues of(RunComparison comparison) {
        return new ComparisonValues(
                comparison.elapsed(), comparison.averagePace(), comparison.fastestKm(), null, null);
    }

    /** 아무 값도 공개되지 않음(상대 기록이 아직 없거나 지워짐). */
    public static ComparisonValues undisclosed() {
        return new ComparisonValues(null, null, null, null, null);
    }

    /** 랜덤 대결 상대: 그 참가자의 기록 값과 추월 횟수. */
    static ComparisonValues ofRival(RunComparison comparison, BattleParticipant rival) {
        return new ComparisonValues(
                comparison.elapsed(),
                comparison.averagePace(),
                comparison.fastestKm(),
                rival.getOvertakes(),
                null);
    }

    /** 기록 상대: 상대가 가장 앞섰을 때 거리 차 = 러너가 가장 뒤처졌을 때 거리 차. */
    ComparisonValues withMaxLead(Distance lead) {
        return new ComparisonValues(elapsed, averagePace, fastestKm, overtakes, lead);
    }

    public Optional<Duration> elapsedValue() {
        return Optional.ofNullable(elapsed);
    }

    public Optional<Pace> averagePaceValue() {
        return Optional.ofNullable(averagePace);
    }

    public Optional<Pace> fastestKmValue() {
        return Optional.ofNullable(fastestKm);
    }

    public Optional<Integer> overtakesValue() {
        return Optional.ofNullable(overtakes);
    }

    public Optional<Distance> maxLeadValue() {
        return Optional.ofNullable(maxLead);
    }
}
