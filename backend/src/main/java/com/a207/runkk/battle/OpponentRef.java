package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Tier;
import java.time.Duration;
import java.util.Objects;

/**
 * 대결에 남기는 기록 상대 값(설계 6.1.6). 기록이 지워져도(유명인 삭제, 기록 주인 탈퇴) 대결 이력은 이 값으로 남는다(FR-172).
 *
 * @param kind 상대 기록 구분
 * @param recordId 상대 기록 ID(러닝 기록 UUID, 가상·유명인 기록 ID 문자열)
 * @param displayName 상대 표시명
 * @param displayTier 상대 티어(유명인은 UNRANKED)
 * @param arrivalElapsed 대결 거리 도달 경과 시간
 */
public record OpponentRef(
        OpponentKind kind,
        String recordId,
        DisplayName displayName,
        Tier displayTier,
        Duration arrivalElapsed) {

    public OpponentRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(displayTier, "displayTier");
        if (arrivalElapsed == null || arrivalElapsed.isNegative() || arrivalElapsed.isZero()) {
            throw new IllegalArgumentException("상대 도착 시간은 0보다 커야 한다: " + arrivalElapsed);
        }
    }
}
