package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.util.List;
import java.util.Map;

/**
 * SignUpForm·GuestStartForm이 함께 쓰는 필수 동의 검사(설계 6.1.2 RequiredConsents, FR-003, FR-004, srs.md 6.3
 * 1.2 의사결정표). 필수 동의(약관·개인정보·위치)가 먼저이고 그다음 만 14세 확인이다. 빠진 항목은 거짓으로 본다.
 */
final class RequiredConsents {

    private static final List<ConsentItem> REQUIRED_DOCUMENTS =
            List.of(ConsentItem.TERMS, ConsentItem.PRIVACY, ConsentItem.LOCATION);

    private RequiredConsents() {}

    /** 필수 동의 중 거짓·빠짐이 있으면 CONSENT_MISSING, AGE_14_PLUS가 거짓·빠짐이면 UNDER_14. */
    static void check(Map<ConsentItem, Boolean> consents) {
        for (ConsentItem item : REQUIRED_DOCUMENTS) {
            if (!Boolean.TRUE.equals(consents.get(item))) {
                throw new BusinessRuleException(ReasonCode.CONSENT_MISSING);
            }
        }
        if (!Boolean.TRUE.equals(consents.get(ConsentItem.AGE_14_PLUS))) {
            throw new BusinessRuleException(ReasonCode.UNDER_14);
        }
    }

    /** 가입·게스트 시작 때 받는 동의 6개를 빠짐없이 채운다(빠지면 거짓). */
    static Map<ConsentItem, Boolean> atSignUp(Map<ConsentItem, Boolean> consents) {
        Map<ConsentItem, Boolean> filled = new java.util.EnumMap<>(ConsentItem.class);
        for (ConsentItem item : ConsentItem.values()) {
            if (item.collectedAtSignUp()) {
                filled.put(item, Boolean.TRUE.equals(consents.get(item)));
            }
        }
        return filled;
    }
}
