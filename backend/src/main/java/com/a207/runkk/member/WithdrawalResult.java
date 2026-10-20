package com.a207.runkk.member;

/**
 * 탈퇴 결과(srs.md 6.3 1.5): 탈퇴완료 / 거부 + 사유.
 *
 * @param withdrawn 탈퇴했는가
 * @param rejectionReason 거부 사유(WithdrawalGuard가 준 문구), 탈퇴했으면 null
 */
public record WithdrawalResult(boolean withdrawn, String rejectionReason) {

    public static WithdrawalResult completed() {
        return new WithdrawalResult(true, null);
    }

    public static WithdrawalResult rejected(String reason) {
        return new WithdrawalResult(false, reason);
    }
}
