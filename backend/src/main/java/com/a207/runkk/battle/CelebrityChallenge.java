package com.a207.runkk.battle;

import java.time.LocalDateTime;

/**
 * 셀럽 기록 하나에 대한 회원의 기간 안 도전 결과(event 이벤트 응모·코치 승리 포인트, S15P31A207-275).
 *
 * @param celebrityId 유명인 기록 ID
 * @param name 유명인 표시명, 기록이 지워졌으면 null
 * @param firstWonAt 기간 안 첫 승리의 판정 시각, 이기지 못했으면 null
 */
public record CelebrityChallenge(long celebrityId, String name, LocalDateTime firstWonAt) {}
