package com.a207.runkk.crew.chat;

/**
 * 신고용 메시지 조회 결과(설계 6.1.7a CrewChatService.reportedView, D-135). report.target의 ChatReportAdapter만
 * 쓴다.
 *
 * @param messageId 메시지 ID
 * @param crewId 크루 ID
 * @param authorId 작성자 회원 ID, 탈퇴면 null
 * @param preview 내용 앞 100자(코드 포인트)
 * @param hidden 신고로 숨겨져 있음
 */
public record ReportedChatView(
        long messageId, long crewId, Long authorId, String preview, boolean hidden) {}
