package com.a207.runkk.member;

/**
 * 설정 변경 결과(srs.md 6.3 1.4 설정결과 = 설정항목 + 처리상태).
 *
 * @param item 설정 항목
 * @param outcome 바꿨는가, 먼저 받을 동의
 * @param settings 처리 뒤 설정 사본
 */
public record SettingResult(
        SettingItem item, SettingOutcome outcome, MemberSettingsView settings) {}
