package com.a207.runkk.member;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 가입 요청 값(설계 6.1.2 SignUpForm, FR-003, D-90). v0.10의 출생연도·성별·사진·소개·공개 범위·태그는 지웠다(프로필 편집으로만 넣는다).
 * 닉네임은 검사하지 않는다: 앱이 보낸 제공자 이름을 SignUpService가 Nickname.fromProviderName으로 고친다(D-143).
 *
 * @param nickname 닉네임(제공자 이름), 없으면 서비스가 정한다
 * @param consents 동의 항목 → 동의 여부
 */
public record SignUpForm(String nickname, Map<ConsentItem, Boolean> consents) {

    public SignUpForm {
        consents =
                consents == null || consents.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(new EnumMap<>(consents));
    }

    /** RequiredConsents.check: CONSENT_MISSING, UNDER_14(srs.md 6.3 1.2 규칙 1·2). */
    public void validate() {
        RequiredConsents.check(consents);
    }
}
