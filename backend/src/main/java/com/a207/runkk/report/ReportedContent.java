package com.a207.runkk.report;

import java.util.Set;

/**
 * 신고 대상 콘텐츠의 판정 자료(설계 6.1.12a, D-104, D-115). report가 crew에 묻지 않도록 포트 구현이 구성원 판정 자료까지 채운다.
 *
 * @param crewId 대상 크루 ID
 * @param authorId 작성자 회원 ID, 탈퇴면 null
 * @param preview 본문 앞 100자 이하
 * @param hidden 신고로 숨겨져 있음
 * @param crewMemberIds 대상 크루의 현재 구성원
 */
public record ReportedContent(
        long crewId, Long authorId, String preview, boolean hidden, Set<Long> crewMemberIds) {

    public ReportedContent {
        crewMemberIds = crewMemberIds == null ? Set.of() : Set.copyOf(crewMemberIds);
    }

    /** 그 회원이 대상 크루의 현재 구성원인가. */
    public boolean isCrewMember(long memberId) {
        return crewMemberIds.contains(memberId);
    }
}
