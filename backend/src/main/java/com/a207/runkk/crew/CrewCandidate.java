package com.a207.runkk.crew;

import com.a207.runkk.common.TagSet;
import java.time.LocalDateTime;

/**
 * 추천 후보 크루(설계 6.1.7 CrewRecommender). 후보 선정(공개, 정원 미달, 본인 소속 아님)은 CrewQueryService가 한다.
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param tags 크루 성향 태그
 * @param headcount 인원
 * @param joinPolicy 가입 방식
 * @param createdAt 생성 일시(같은 일치도면 최근 생성순)
 */
public record CrewCandidate(
        long crewId,
        String name,
        TagSet tags,
        int headcount,
        JoinPolicy joinPolicy,
        LocalDateTime createdAt) {}
