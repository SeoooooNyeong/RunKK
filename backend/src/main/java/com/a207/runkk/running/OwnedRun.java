package com.a207.runkk.running;

/**
 * 고스트 후보 기록(설계 6.1.3, A-07, D-51).
 *
 * @param recordId 기록 ID
 * @param memberId 주인 회원 ID
 */
public record OwnedRun(String recordId, long memberId) {}
