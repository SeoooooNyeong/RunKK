package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 크루 초대 코드(설계 6.1.7, FR-112, FR-114). 6자리 영문 대문자·숫자이고 발급 뒤 7일 유효하다.
 *
 * @param code 6자리 코드
 * @param expiresAt 만료 일시(발급 + 7일)
 */
@Embeddable
public record InviteCode(
        @Column(name = "invite_code", nullable = false, length = 6) String code,
        @Column(name = "invite_code_expires_at", nullable = false) LocalDateTime expiresAt) {

    /** 유효 기간(FR-112). */
    static final int VALID_DAYS = 7;

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]{6}");

    public InviteCode {
        if (code == null || !FORMAT.matcher(code).matches() || expiresAt == null) {
            throw new IllegalArgumentException("초대 코드는 6자리 영문 대문자·숫자: " + code);
        }
    }

    /** now에 발급한 코드. 만료는 now + 7일. */
    public static InviteCode issue(String code, LocalDateTime now) {
        return new InviteCode(code.toUpperCase(Locale.ROOT), now.plusDays(VALID_DAYS));
    }

    public boolean isValidAt(LocalDateTime now) {
        return now.isBefore(expiresAt);
    }

    /** 대소문자 구분 없이 같은지. */
    public boolean matches(String input) {
        return input != null && code.equalsIgnoreCase(input.strip());
    }
}
