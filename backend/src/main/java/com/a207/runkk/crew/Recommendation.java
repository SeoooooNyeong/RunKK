package com.a207.runkk.crew;

/**
 * 추천 한 건(자료사전 추천크루목록의 한 항목).
 *
 * @param crew 크루
 * @param matchCount 일치도(겹치는 태그 수)
 */
public record Recommendation(CrewCandidate crew, int matchCount) {}
