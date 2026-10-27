package com.a207.runkk.member;

import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.RecordVisibility;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 회원 설정의 읽기 전용 사본(설계 6.1.2, design-review.md A44). GET /me/settings로 앱 설정 사본(SettingsRepository)이
 * 읽는 원본이라 동의 여부와 출생연도도 싣는다(D-53, D-57). v0.11에서 화면 테마·계정 유형·연동 제공자를 더했다(D-90, D-92). 고쳐도 Member는
 * 바뀌지 않는다.
 *
 * @param unit 거리 단위
 * @param recordVisibility 기록 공개 범위
 * @param courseMapShared 코스 지도 공유
 * @param profileVisibility 개인정보 공개
 * @param notificationPrefs 알림 종류 이름 → 수신 여부(21개, 변경 불가)
 * @param watchLinked 워치 연동
 * @param runSettings 자유런 설정
 * @param battleSettings 배틀런 설정
 * @param consents 동의 항목 → 동의 여부(8개, 변경 불가)
 * @param birthYear 출생연도, 없을 수 있음
 * @param theme 화면 테마(FR-203)
 * @param accountType 계정 유형(본인 전용 응답에만, FR-006, FR-199)
 * @param linkedProvider 연동한 소셜 제공자, 게스트면 null
 */
public record MemberSettingsView(
        DistanceUnit unit,
        RecordVisibility recordVisibility,
        boolean courseMapShared,
        ProfileVisibility profileVisibility,
        Map<String, Boolean> notificationPrefs,
        boolean watchLinked,
        RunSettings runSettings,
        BattleSettings battleSettings,
        Map<ConsentItem, Boolean> consents,
        Integer birthYear,
        ScreenTheme theme,
        AccountType accountType,
        ProviderType linkedProvider) {

    public MemberSettingsView {
        notificationPrefs = Collections.unmodifiableMap(new LinkedHashMap<>(notificationPrefs));
        consents = Collections.unmodifiableMap(new EnumMap<>(consents));
    }
}
