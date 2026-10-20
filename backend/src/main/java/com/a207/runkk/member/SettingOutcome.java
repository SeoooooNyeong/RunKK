package com.a207.runkk.member;

/**
 * Member.changeSetting의 결과(설계 6.1.2). 워치 연동을 켜는데 심박 동의가 없으면 바꾸지 않고 필요한 동의를 알린다(FR-019).
 *
 * @param applied 바꿨는가
 * @param requiredConsent 먼저 받아야 할 동의, 바꿨으면 null
 */
public record SettingOutcome(boolean applied, ConsentItem requiredConsent) {

    public static SettingOutcome changed() {
        return new SettingOutcome(true, null);
    }

    public static SettingOutcome needsConsent(ConsentItem item) {
        return new SettingOutcome(false, item);
    }
}
