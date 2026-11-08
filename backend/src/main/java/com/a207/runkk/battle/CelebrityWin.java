package com.a207.runkk.battle;

import java.time.LocalDateTime;

/**
 * 회원 한 명이 셀럽 기록 하나를 기간 안에 처음 이긴 판정(event 이벤트 응모자 명단, S15P31A207-275).
 *
 * @param memberId 이긴 회원 ID
 * @param celebrityId 유명인 기록 ID
 * @param firstWonAt 기간 안 첫 승리의 판정 시각
 */
public record CelebrityWin(long memberId, long celebrityId, LocalDateTime firstWonAt) {}
