package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.Objects;

/**
 * 상대 표의 표본 하나: 경과 시간과 그때의 누적 거리(설계 6.1.6 OpponentTrack). 위치 좌표가 없다(FR-089).
 *
 * @param elapsed 0 이상의 경과 시간
 * @param distance 누적 거리
 */
public record TrackSample(Duration elapsed, Distance distance) {

    public TrackSample {
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(distance, "distance");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("경과 시간은 0 이상이어야 한다: " + elapsed);
        }
    }
}
