package com.a207.runkk.battle;

import java.time.Duration;

/**
 * 참가자 기록 검증 값(05-21 D-482). Battle.verify의 매개변수를 묶는다.
 *
 * @param recordId 러닝 기록 ID
 * @param valid 유효 러닝이고 대결 거리에 닿았는가(판정용 검증값)
 * @param reachElapsed 기록상 대결 거리 도달 경과 시간, 없으면 null
 * @param validRunOrdinal 그 기록의 유효 러닝 순번(무효면 0)
 */
public record RecordCheck(
        String recordId, boolean valid, Duration reachElapsed, int validRunOrdinal) {}
