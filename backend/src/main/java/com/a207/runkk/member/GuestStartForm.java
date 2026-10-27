package com.a207.runkk.member;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 게스트 시작 요청 값(설계 6.1.2 GuestStartForm, FR-197, FR-004). 필수 AGE_14_PLUS·TERMS·PRIVACY·LOCATION, 선택
 * MARKETING·LLM_TRANSFER(빠지면 거짓). HEART_RATE·FACE_TRANSFER는 받지 않는다(그 기능을 처음 쓸 때 동의 변경으로 받는다).
 *
 * @param consents 동의 항목 → 동의 여부
 */
public record GuestStartForm(Map<ConsentItem, Boolean> consents) {

    public GuestStartForm {
        consents =
                consents == null || consents.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(new EnumMap<>(consents));
    }

    /**
     * 가입 때 받지 않는 동의 항목이 있으면 IllegalArgumentException(400, D-164 (4)). 입력 형식 검사라 업무 규칙(필수 동의)보다 먼저
     * 본다. 그다음 RequiredConsents.check(CONSENT_MISSING, UNDER_14).
     */
    public void validate() {
        for (ConsentItem item : consents.keySet()) {
            if (!item.collectedAtSignUp()) {
                throw new IllegalArgumentException("게스트 시작 때 받지 않는 동의 항목: " + item);
            }
        }
        RequiredConsents.check(consents);
    }
}
