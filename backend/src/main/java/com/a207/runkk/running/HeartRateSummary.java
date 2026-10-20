package com.a207.runkk.running;

/**
 * 심박 요약(설계 6.1.3 HeartRateSeries.summary). 원자료를 파기해도 남는다(FR-029).
 *
 * @param average 평균 bpm
 * @param max 최고 bpm
 */
public record HeartRateSummary(int average, int max) {}
