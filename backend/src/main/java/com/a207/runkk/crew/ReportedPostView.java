package com.a207.runkk.crew;

/**
 * 신고용 게시글 조회 결과(D-135). report.target의 CrewPostReportAdapter만 쓴다. 숨긴 글도 돌려준다.
 *
 * @param postId 게시글 ID
 * @param crewId 크루 ID
 * @param kind 게시 종류
 * @param authorId 작성자, 탈퇴했으면 null
 * @param preview 본문 앞 100자
 * @param hidden 신고 숨김 중인가
 */
public record ReportedPostView(
        long postId, long crewId, PostKind kind, Long authorId, String preview, boolean hidden) {}
