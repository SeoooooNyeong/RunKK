package com.a207.runkk.report;

/**
 * 신고 접수 입력(설계 6.1.12a, D-127, D-143, D-149). 신고내용은 앞뒤 공백을 지운 뒤 코드 포인트로 세어 200자 이하이고, 사유가 OTHER면 1자
 * 이상 필수다. 지운 뒤 비면 null로 둔다. 위반은 IllegalArgumentException(→ 400 BAD_REQUEST).
 *
 * @param targetKind 대상 종류
 * @param targetId 게시글 ID 또는 채팅 메시지 ID
 * @param reason 사유
 * @param detail 신고내용, 없으면 null
 */
public record ReportCommand(
        ReportTargetKind targetKind, long targetId, ReportReason reason, String detail) {

    /** 신고내용 글자 수 상한(코드 포인트, srs FR-214 보완). */
    public static final int MAX_DETAIL = 200;

    public ReportCommand {
        if (targetKind == null || reason == null) {
            throw new IllegalArgumentException("대상 종류·사유가 비었다");
        }
        if (targetId < 1) {
            throw new IllegalArgumentException("대상 ID는 양수: " + targetId);
        }
        String trimmed = detail == null ? "" : detail.strip();
        if (reason == ReportReason.OTHER && trimmed.isEmpty()) {
            throw new IllegalArgumentException("기타 사유는 신고내용이 필요하다");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_DETAIL) {
            throw new IllegalArgumentException("신고내용은 " + MAX_DETAIL + "자까지");
        }
        detail = trimmed.isEmpty() ? null : trimmed;
    }
}
