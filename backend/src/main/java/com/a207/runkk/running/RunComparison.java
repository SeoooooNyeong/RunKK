package com.a207.runkk.running;

import com.a207.runkk.common.Pace;
import java.time.Duration;

/**
 * 대결 결과 비교표에 쓸 기록 한 건의 값(설계 6.1.3, R5).
 *
 * @param elapsed 경과 시간
 * @param averagePace 평균 페이스
 * @param fastestKm 가장 빠른 1km(없으면 null)
 */
public record RunComparison(Duration elapsed, Pace averagePace, Pace fastestKm) {}
