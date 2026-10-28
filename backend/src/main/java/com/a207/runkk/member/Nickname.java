package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.Optional;

/**
 * 닉네임(설계 6.1.2). 앞뒤 공백을 지운 2~20자. 글자 수는 보이는 글자(코드 포인트)로 세고 자를 때도 코드 포인트 경계에서 자른다(D-143).
 *
 * @param value 닉네임 문자열
 */
@Embeddable
public record Nickname(@Column(name = "nickname", length = 20) String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final int SUFFIX_DIGITS = 4;
    private static final int MAX_SUFFIX = 9999;
    private static final int MAX_NUMBER = 99_999;
    private static final String DEFAULT_PREFIX = "러너";

    public Nickname {
        if (value == null) {
            throw new BusinessRuleException(ReasonCode.NICKNAME_INVALID);
        }
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new BusinessRuleException(ReasonCode.NICKNAME_INVALID);
        }
    }

    /** 2~20자가 아니면 BusinessRuleException(NICKNAME_INVALID). */
    public static Nickname of(String v) {
        return new Nickname(v == null ? null : v.strip());
    }

    /**
     * 가입 닉네임(제공자 이름)을 고친다(FR-003, D-143). 앞뒤 공백을 지우고 2자 미만이면 비어 있음, 20자를 넘으면 앞 20자. 예외를 던지지 않는다.
     */
    public static Optional<Nickname> fromProviderName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String stripped = raw.strip();
        if (stripped.codePointCount(0, stripped.length()) < MIN_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new Nickname(headCodePoints(stripped, MAX_LENGTH)));
    }

    /**
     * 기본 닉네임 "러너" + 숫자(FR-003, FR-197, D-90). number가 10000 미만이면 4자리(0을 채움), 그 이상이면 5자리. 0~99999만
     * 받는다.
     */
    public static Nickname numbered(int number) {
        if (number < 0 || number > MAX_NUMBER) {
            throw new IllegalArgumentException("기본 닉네임 숫자는 0~99999: " + number);
        }
        return new Nickname(DEFAULT_PREFIX + String.format(Locale.ROOT, "%04d", number));
    }

    /** 숫자 4자리를 붙인다. 20자를 넘으면 앞부분을 코드 포인트 경계에서 잘라 20자에 맞춘다(Q-75, D-143). */
    public Nickname withSuffix(int fourDigits) {
        if (fourDigits < 0 || fourDigits > MAX_SUFFIX) {
            throw new IllegalArgumentException("숫자 4자리는 0~9999: " + fourDigits);
        }
        String base = headCodePoints(value, MAX_LENGTH - SUFFIX_DIGITS);
        return new Nickname(base + String.format(Locale.ROOT, "%04d", fourDigits));
    }

    private static String headCodePoints(String text, int count) {
        if (text.codePointCount(0, text.length()) <= count) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, count));
    }
}
