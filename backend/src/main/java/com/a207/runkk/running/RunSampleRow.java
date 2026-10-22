package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;

/**
 * Runimal 표본에 쓸 유효 러닝 한 건의 최소 값(설계 6.1.3, D-41).
 *
 * @param recordId 기록 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 */
public record RunSampleRow(String recordId, LocalDateTime startedAt, Distance distance) {}
