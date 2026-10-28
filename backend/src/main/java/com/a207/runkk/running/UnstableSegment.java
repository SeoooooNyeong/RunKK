package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;

/**
 * 신호 불안정 구간 하나(FR-033). 경로를 파기해도 시각·거리는 남는다.
 *
 * @param from 시작 일시
 * @param to 끝 일시, from 이후
 * @param distance 구간 직선 거리(누적 거리에서 뺀 값)
 */
public record UnstableSegment(LocalDateTime from, LocalDateTime to, Distance distance) {

    public UnstableSegment {
        if (from == null || to == null || distance == null || to.isBefore(from)) {
            throw new IllegalArgumentException("불안정 구간 형식 오류");
        }
    }
}
