package com.a207.runkk.profile;

import com.a207.runkk.common.Distance;
import com.a207.runkk.running.CardKind;
import java.time.LocalDate;

/**
 * 다른 러너 프로필의 최근 러닝 한 건(FR-060, v0.14 D-243). 유효 기록만이고 기록 공개 범위를 통과할 때만 싣는다.
 *
 * @param kind 카드 종류
 * @param startedOn 러닝 날짜
 * @param distance 거리
 */
public record RecentRun(CardKind kind, LocalDate startedOn, Distance distance) {

    /** 카드 종류 이름(표현 계층이 running을 import하지 않게 넘긴다). */
    public String kindName() {
        return kind.name();
    }
}
