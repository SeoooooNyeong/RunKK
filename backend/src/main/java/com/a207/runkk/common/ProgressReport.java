package com.a207.runkk.common;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 실시간 진행값(설계 6.1.1). 위치 필드가 없다(FR-089). 채널 구분과 채널 ID는 realtime의 InboundProgress에 있다(D-48).
 *
 * @param distance 누적 거리
 * @param elapsed 경과 시간
 * @param sentAt 보낸 시각
 */
public record ProgressReport(Distance distance, Duration elapsed, LocalDateTime sentAt) {}
