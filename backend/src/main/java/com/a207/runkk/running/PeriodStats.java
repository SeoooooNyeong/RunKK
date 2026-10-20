package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.LocalDate;

/**
 * 한 기간(주·월)의 유효 러닝 통계(성장 분석).
 *
 * @param from 시작일
 * @param to 종료일
 * @param distance 거리 합
 * @param count 횟수
 * @param averagePace 평균 페이스(기록이 없으면 null)
 */
public record PeriodStats(
        LocalDate from, LocalDate to, Distance distance, int count, Pace averagePace) {}
