package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;

/**
 * 매칭 접수 결과(자료사전 매칭접수결과 = 대기등록 | 대기해제 | 거부 + 거부사유). 거부는 BusinessRuleException 사유 코드로 알린다.
 *
 * @param waiting 대기 중이면 true(대기등록), 대기를 풀었으면 false(대기해제)
 * @param distance 대기 거리, 대기해제면 null
 */
public record MatchAcceptance(boolean waiting, StandardDistance distance) {

    static MatchAcceptance waitingFor(StandardDistance distance) {
        return new MatchAcceptance(true, distance);
    }

    static MatchAcceptance released() {
        return new MatchAcceptance(false, null);
    }
}
