package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;

/**
 * Runimal 표본에 쓸 유효 러닝 한 건의 최소 값(설계 6.1.3, D-41).
 *
 * @param recordId 기록 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param battleRun 배틀런 기록(카드 종류 BATTLE_RUN이고 대결 ID가 있다: 랜덤·고스트·친구 대결, v0.14 D-247)
 */
public record RunSampleRow(
        String recordId, LocalDateTime startedAt, Distance distance, boolean battleRun) {

    /** 배틀런이 아닌 표본. */
    public RunSampleRow(String recordId, LocalDateTime startedAt, Distance distance) {
        this(recordId, startedAt, distance, false);
    }

    /** 카드 종류와 대결 ID로 배틀런 여부를 정한다(D-247). */
    public static RunSampleRow of(
            String recordId,
            LocalDateTime startedAt,
            Distance distance,
            CardKind cardKind,
            Long battleId) {
        return new RunSampleRow(
                recordId, startedAt, distance, cardKind == CardKind.BATTLE_RUN && battleId != null);
    }
}
