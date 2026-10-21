package com.a207.runkk.crew;

import com.a207.runkk.common.Week;

/**
 * 주간 거리 원자적 증가의 열쇠(설계 6.1.7 WeeklyRaceRepository.addAtomically, A-12). (crewId, week, recordId)가 한
 * 번만 들어간다.
 *
 * @param crewId 크루 ID
 * @param week 주
 * @param memberId 기록 주인
 * @param recordId 기록 ID
 */
public record RaceContributionKey(long crewId, Week week, long memberId, String recordId) {

    public RaceContributionKey {
        if (week == null || recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("주·기록 ID가 비었다");
        }
    }
}
