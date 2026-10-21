package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 러닝 기록 한 건이 저장되었음을 알리는 사건(설계 6.1.1 사건 표, D-31). validRunOrdinal은 이 기록을 포함한 그 회원의 유효 러닝 순번이고 무효면
 * 0이다 (D-37). occurredAt은 저장 일시다(DomainEvent).
 *
 * @param recordId 기록 ID
 * @param memberId 회원 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param elapsed 경과 시간
 * @param valid 유효 러닝
 * @param score 러닝 점수(무효면 null)
 * @param mode 러닝 모드
 * @param battleId 대결 ID
 * @param crewRunPostId 크루런 게시글 ID
 * @param hasRoute 경로가 있는가
 * @param validRunOrdinal 유효 러닝 순번(무효면 0)
 * @param occurredAt 저장 일시
 */
public record RunRecordSaved(
        String recordId,
        long memberId,
        LocalDateTime startedAt,
        Distance distance,
        Duration elapsed,
        boolean valid,
        Double score,
        RunMode mode,
        Long battleId,
        Long crewRunPostId,
        boolean hasRoute,
        int validRunOrdinal,
        LocalDateTime occurredAt)
        implements DomainEvent {

    static RunRecordSaved of(RunRecord r, int validRunOrdinal, LocalDateTime at) {
        return new RunRecordSaved(
                r.getRecordId(),
                r.getMemberId(),
                r.getStartedAt(),
                r.getDistance(),
                r.getElapsed(),
                r.isValid(),
                r.getScore() == null ? null : r.getScore().value(),
                r.getMode(),
                r.battleId(),
                r.crewRunPostId(),
                r.hasRoute(),
                validRunOrdinal,
                at);
    }
}
