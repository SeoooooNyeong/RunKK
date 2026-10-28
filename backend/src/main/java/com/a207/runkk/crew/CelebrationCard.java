package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;

/**
 * 공동 목표 달성 축하 카드(FR-141, D-105). 저장하지 않고 조회 때 만든다.
 *
 * @param crewName 크루명
 * @param totalMeters 그 주 합산 거리
 * @param goalMeters 그 주 목표
 * @param participantCount 참여 인원(그 주 기여가 있는 회원 수)
 */
public record CelebrationCard(
        String crewName, Distance totalMeters, Distance goalMeters, int participantCount) {}
