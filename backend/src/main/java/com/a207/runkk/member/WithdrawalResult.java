package com.a207.runkk.member;

/**
 * 탈퇴 결과(srs.md 6.3 1.5): 탈퇴완료. 거부는 WithdrawalService가 BusinessRuleException(사유 코드)으로 던져 409 공통 오류
 * 응답(code·message)이 된다(앱–서버 대조 B 단계). rejectionReason은 앱 하위 호환으로 남긴 항목이고 늘 null이다.
 *
 * @param withdrawn 탈퇴했는가(늘 true)
 * @param rejectionReason 늘 null(거부는 오류 응답 code로 온다)
 */
public record WithdrawalResult(boolean withdrawn, String rejectionReason) {

    public static WithdrawalResult completed() {
        return new WithdrawalResult(true, null);
    }
}
