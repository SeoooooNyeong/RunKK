package com.a207.runkk.crew;

/**
 * 다른 패키지에 내주는 크루 표시 정보(설계 6.1.7 CrewMembershipReader, design-review.md M5). profile은 세 항목, analysis
 * 대시보드는 crewId·name을 쓴다.
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param logoUrl 로고, 없을 수 있음
 */
public record CrewSummary(long crewId, String name, String logoUrl) {}
