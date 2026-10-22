package com.a207.runkk.policy;

/**
 * 운영 기준값 검사 결과(SettingKey.validate·validateTransition의 반환 형, 설계 1.4.6).
 *
 * @param valid 통과 여부
 * @param reason 거부 이유(운영 로그용, 통과면 빈 문자열)
 */
public record ValidationResult(boolean valid, String reason) {

    private static final ValidationResult OK = new ValidationResult(true, "");

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult invalid(String reason) {
        return new ValidationResult(false, reason);
    }
}
