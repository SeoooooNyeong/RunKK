package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.TagSet;
import java.time.LocalDateTime;

/**
 * 크루 목록 한 줄의 crew 소유 자료(설계 6.1.7 CrewRepository.search, 6.5). crew·crew_membership·weekly_race만
 * 읽는다. 크루장 표시명은 비어 있고 CrewQueryService가 MemberQueryService.summaries로 붙인다(member 테이블 조인 없음, D-32).
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 크루 성향 태그
 * @param logoUrl 로고
 * @param headcount 인원
 * @param joinPolicy 가입 방식
 * @param leaderId 크루장 회원 ID
 * @param weeklyDistance 이번 주 크루 거리(W-16)
 * @param createdAt 생성 일시
 */
public record CrewListRow(
        long crewId,
        String name,
        String intro,
        TagSet tags,
        String logoUrl,
        int headcount,
        JoinPolicy joinPolicy,
        long leaderId,
        Distance weeklyDistance,
        LocalDateTime createdAt) {}
