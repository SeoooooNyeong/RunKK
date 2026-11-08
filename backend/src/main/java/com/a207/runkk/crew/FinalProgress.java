package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import java.time.Duration;

/**
 * 앱이 자기 크루런을 끝낼 때 보내는 마지막 거리·경과(합산 방식, POST /crew-runs/{id}/quit 본문). 결과 화면이 기록 업로드 전에도 이 값을 쓴다.
 *
 * @param distance 마지막 누적 거리
 * @param elapsed 마지막 경과(운동 시간)
 */
public record FinalProgress(Distance distance, Duration elapsed) {

    /** 받아들이는 가장 긴 거리(100km). */
    static final Distance MAX_DISTANCE = Distance.ofMeters(100_000);

    /** 받아들이는 가장 긴 경과(24시간). */
    static final Duration MAX_ELAPSED = Duration.ofHours(24);

    /** 거리 0~100km, 경과 0~24시간이 아니면 CREW_RUN_INVALID. */
    void validate() {
        if (distance == null
                || elapsed == null
                || distance.compareTo(MAX_DISTANCE) > 0
                || elapsed.isNegative()
                || elapsed.compareTo(MAX_ELAPSED) > 0) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
    }
}
