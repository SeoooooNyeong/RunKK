package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;

/**
 * 회원 누적 통계.
 *
 * @param distance 누적 거리(무효 포함)
 * @param elapsed 누적 시간(무효 포함)
 * @param count 횟수(무효 포함)
 * @param validDistance 누적 유효 거리(랜드마크·레벨)
 */
public record TotalStats(Distance distance, Duration elapsed, int count, Distance validDistance) {}
