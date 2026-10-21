package com.a207.runkk.app.domain.settings

/**
 * 서버 설정 사본(GET /me/settings, D-53). 사본에는 서버 회원 설정 외의 값을 넣지 않는다.
 */
data class SettingsSnapshot(
    val isLocationConsented: Boolean,
    val isHeartRateConsented: Boolean,
    val isWatchLinked: Boolean,
    val isCourseMapShared: Boolean,
    val birthYear: Int?,
    val guidanceSettings: GuidanceSettings
) {
    companion object {
        /** 사본이 없을 때 쓰는 개인정보 쪽으로 안전한 값(동의 거짓, 공유 거짓). */
        val SAFE =
            SettingsSnapshot(
                isLocationConsented = false,
                isHeartRateConsented = false,
                isWatchLinked = false,
                isCourseMapShared = false,
                birthYear = null,
                guidanceSettings = GuidanceSettings.DEFAULT
            )
    }
}
