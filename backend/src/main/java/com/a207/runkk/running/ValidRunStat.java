package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.LocalDateTime;

/**
 * 유효 러닝 한 건의 비교용 값(런바디 요약 "최근 10회 변화", G-14). 위치·심박 원자료를 읽지 않는다.
 *
 * @param ordinal 유효 러닝 순번(1 이상, 저장 일시·기록 ID 순, D-108과 같다)
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param averagePace 평균 페이스
 * @param averageHeartRate 평균 심박(bpm). 심박이 없는 기록이면 null
 */
public record ValidRunStat(
        int ordinal,
        LocalDateTime startedAt,
        Distance distance,
        Pace averagePace,
        Integer averageHeartRate) {

    public ValidRunStat {
        if (ordinal < 1 || startedAt == null || distance == null || averagePace == null) {
            throw new IllegalArgumentException("유효 러닝 값 오류: " + ordinal);
        }
    }
}
