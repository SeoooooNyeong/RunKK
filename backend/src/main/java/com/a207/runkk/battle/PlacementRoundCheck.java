package com.a207.runkk.battle;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 배치고사 판 인정 판단(05-21 D-511). 판 셈(D-481)·복구(D-490)·결과 상태(D-487)가 이 한 값을 쓴다. 판정용 검증값(참가자 verified =
 * 유효 && 대결 거리 도달)은 쓰지 않고 붙은 기록의 RunSummary.valid만 본다: 유령보다 늦어 대결 거리에 못 닿은 유효 기록도 센다(R82).
 *
 * @param finished 대결이 FINISHED인가
 * @param cancelled 대결이 CANCELLED인가(출발 전 취소)
 * @param forfeited 본인이 포기했는가
 * @param hasRecord 본인 참가자에 기록 ID가 붙었는가
 * @param recordValid 붙은 기록의 RunSummary.valid, 기록이 없거나 요약을 읽지 못하면 null
 * @param expiredWithoutRecord FINISHED이고 기록 ID 없이 검증값이 거짓(24시간 보류 만료 판정)
 * @param summaryMissingExpired 기록 ID는 붙었는데 요약을 읽지 못한 채 판정 뒤 24시간이 지났다(TF-V18-01 총괄 결정, 유예 뒤
 *     NOT_COUNTED)
 */
public record PlacementRoundCheck(
        boolean finished,
        boolean cancelled,
        boolean forfeited,
        boolean hasRecord,
        Boolean recordValid,
        boolean expiredWithoutRecord,
        boolean summaryMissingExpired) {

    /** 늦은 기록 인정 창(판정 뒤 요약을 읽지 못한 판을 닫기까지, Battle.PENDING_LIMIT와 같다). */
    static final Duration SUMMARY_GRACE = Battle.PENDING_LIMIT;

    /** 대결과 본인 참가자, 붙은 기록의 유효 여부(없거나 읽지 못하면 null)로 만든다. 요약 유예는 보지 않는다. */
    public static PlacementRoundCheck of(Battle battle, long memberId, Boolean recordValid) {
        return of(battle, memberId, recordValid, null);
    }

    /**
     * now가 있으면 요약을 읽지 못한 판이 판정 뒤 SUMMARY_GRACE를 넘겼는지도 본다(TF-V18-01). 참가자가 아니면
     * IllegalArgumentException.
     */
    public static PlacementRoundCheck of(
            Battle battle, long memberId, Boolean recordValid, LocalDateTime now) {
        BattleParticipant me =
                battle.participant(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("참가자가 아니다: " + memberId));
        boolean finished = battle.getStatus() == BattleStatus.FINISHED;
        boolean hasRecord = me.getRecordId().isPresent();
        boolean expiredWithoutRecord =
                finished && !hasRecord && me.getVerified().map(v -> !v).orElse(false);
        boolean summaryMissingExpired =
                finished
                        && hasRecord
                        && recordValid == null
                        && now != null
                        && battle.getJudgement()
                                .map(j -> !now.isBefore(j.at().plus(SUMMARY_GRACE)))
                                .orElse(false);
        return new PlacementRoundCheck(
                finished,
                battle.isCancelled(),
                me.isForfeited(),
                hasRecord,
                hasRecord ? recordValid : null,
                expiredWithoutRecord,
                summaryMissingExpired);
    }

    /** 판으로 셀 수 있다: 끝났고, 포기 아니고, 기록이 붙었고, 그 기록이 유효 러닝이다. */
    public boolean countable() {
        return finished && !forfeited && hasRecord && Boolean.TRUE.equals(recordValid);
    }

    /** 세지 않는 판: 취소, 포기, 붙은 기록이 무효, 기록 없이 만료 판정, 요약을 읽지 못한 채 유예가 지남. */
    public boolean isVoid() {
        return cancelled
                || forfeited
                || Boolean.FALSE.equals(recordValid)
                || expiredWithoutRecord
                || summaryMissingExpired;
    }
}
