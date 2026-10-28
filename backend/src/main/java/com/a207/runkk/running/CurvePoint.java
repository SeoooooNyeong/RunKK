package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;

/**
 * 경과 시간별 누적 거리 표본 하나(RunCurve, 좌표 없음, FR-089).
 *
 * @param elapsed 경과 시간
 * @param distance 누적 거리
 */
public record CurvePoint(Duration elapsed, Distance distance) {}
