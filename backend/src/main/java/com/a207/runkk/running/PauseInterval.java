package com.a207.runkk.running;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 자유런의 정지 구간 하나(설계 6.1.3, 자료사전 정지구간, FR-036, FR-194, D-93). JSON은 {@code pauses[{from, to}]}다.
 *
 * @param from 정지 시작 일시
 * @param to 재개(또는 종료) 일시, from 이후
 */
public record PauseInterval(LocalDateTime from, LocalDateTime to) {

    public PauseInterval {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("정지 구간은 시작이 끝보다 앞이다");
        }
    }

    /** 정지 길이(to − from). */
    public Duration duration() {
        return Duration.between(from, to);
    }

    /**
     * 구간 (a, b)가 이 정지에 걸치는가. a &lt; to 그리고 b &gt; from일 때만 참이고 경계는 포함하지 않는다: 정지 시작·종료 시각과 같은 시각의
     * 포인트는 정상 구간에 속한다(D-163). RunTrack이 정지 경계에 걸친 이웃 포인트 쌍을 뺄 때 쓴다.
     */
    public boolean straddles(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(to) && b.isAfter(from);
    }
}
