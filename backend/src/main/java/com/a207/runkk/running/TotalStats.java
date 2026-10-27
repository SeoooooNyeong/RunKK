package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;

/**
 * 회원 누적 통계. 유효 기록만 센다(v0.10: 집계 제외 기록은 홈 누적에도 넣지 않는다, 달력·이번 달 요약과 같은 기준). 거리·시간은 초과 구간을 뺀 집계
 * 값이다(FR-047).
 *
 * @param distance 누적 유효 거리(홈 누적, 랜드마크·배경 해금)
 * @param elapsed 누적 유효 시간
 * @param count 유효 횟수
 */
public record TotalStats(Distance distance, Duration elapsed, int count) {}
