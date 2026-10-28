package com.a207.runkk.battle;

import java.time.LocalDateTime;

/**
 * 판정용 도착 주장(설계 6.1.6, D-30). 도착 시각은 대결 startAt + 기록상 도달 경과 시간이다. 기록이 아직 없으면 verified가 null이고 실시간으로
 * 본 도착 시각(있으면)을 쓴다.
 *
 * @param side 쪽
 * @param arrivedAt 도착 시각, 도달하지 못했거나 모르면 null
 * @param verified 기록 검증 결과, 기록이 없으면 null
 */
record ArrivalClaim(Side side, LocalDateTime arrivedAt, Boolean verified) {

    boolean hasArrival() {
        return arrivedAt != null;
    }

    boolean hasRecord() {
        return verified != null;
    }

    boolean isValid() {
        return Boolean.TRUE.equals(verified);
    }
}
