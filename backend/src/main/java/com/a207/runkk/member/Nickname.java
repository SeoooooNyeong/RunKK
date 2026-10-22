package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;

/**
 * 닉네임(설계 6.1.2). 앞뒤 공백을 지운 2~20자.
 *
 * @param value 닉네임 문자열
 */
@Embeddable
public record Nickname(@Column(name = "nickname", length = 20) String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final int SUFFIX_DIGITS = 4;
    private static final int MAX_SUFFIX = 9999;

    public Nickname {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new BusinessRuleException(ReasonCode.NICKNAME_INVALID);
        }
    }

    /** 2~20자가 아니면 BusinessRuleException(NICKNAME_INVALID). */
    public static Nickname of(String v) {
        return new Nickname(v == null ? null : v.strip());
    }

    /** 숫자 4자리를 붙인다. 20자를 넘으면 앞부분을 잘라 20자에 맞춘다(Q-75). */
    public Nickname withSuffix(int fourDigits) {
        if (fourDigits < 0 || fourDigits > MAX_SUFFIX) {
            throw new IllegalArgumentException("숫자 4자리는 0~9999: " + fourDigits);
        }
        String base =
                value.length() + SUFFIX_DIGITS > MAX_LENGTH
                        ? value.substring(0, MAX_LENGTH - SUFFIX_DIGITS)
                        : value;
        return new Nickname(base + String.format(Locale.ROOT, "%04d", fourDigits));
    }
}
