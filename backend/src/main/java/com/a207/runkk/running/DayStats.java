package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 하루의 유효 러닝 통계(대시보드 오늘 요약·잔디·스트릭·달력).
 *
 * @param date 날짜
 * @param distance 거리 합
 * @param elapsed 시간 합
 * @param count 횟수
 */
public record DayStats(LocalDate date, Distance distance, Duration elapsed, int count) {}
