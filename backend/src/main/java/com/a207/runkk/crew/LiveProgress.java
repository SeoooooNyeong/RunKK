package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 크루런 참가자의 최신 진행값(CrewRunLiveSession.latest). 위치가 없다.
 *
 * @param distance 누적 거리
 * @param elapsed 경과 시간
 * @param seenAt 받은 시각(3초 연결 확인 기준)
 */
public record LiveProgress(Distance distance, Duration elapsed, LocalDateTime seenAt) {}
