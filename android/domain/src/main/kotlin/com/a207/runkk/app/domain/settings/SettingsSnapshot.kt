package com.a207.runkk.app.domain.settings

/**
 * 서버 설정 사본(GET /me/settings, D-53). 사본에는 서버 회원 설정 외의 값을 넣지 않는다.
 * [isGuest]는 서버 accountType이 GUEST인가다(연동하지 않은 게스트, FR-199). 로그아웃 갈래를 이 값으로 정한다(A1 설계 갱신 요청 1 총괄 결정).
 */
data class SettingsSnapshot(
    val isLocationConsented: Boolean,
    val isHeartRateConsented: Boolean,
    val isWatchLinked: Boolean,
    val isCourseMapShared: Boolean,
    val birthYear: Int?,
    val guidanceSettings: GuidanceSettings,
    val theme: ScreenTheme = ScreenTheme.SYSTEM,
    val isGuest: Boolean = false
) {
    companion object {
        /**
         * 사본이 없을 때 쓰는 값: 개인정보 항목은 안전한 쪽(동의 거짓, 공유 거짓)이고, 테마는 SYSTEM, 근접 알림은 참이다
         * (서버 기본값과 같다, D-162).
         */
        val SAFE =
            SettingsSnapshot(
                isLocationConsented = false,
                isHeartRateConsented = false,
                isWatchLinked = false,
                isCourseMapShared = false,
                birthYear = null,
                guidanceSettings = GuidanceSettings.DEFAULT,
                theme = ScreenTheme.SYSTEM,
                isGuest = false
            )
    }
}
