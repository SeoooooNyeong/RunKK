package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * GET /me/settings 응답(서버 member.MemberSettingsView, D-53). 앱 사본은 이 가운데 동의·워치 연동·코스지도 공유·
 * 출생연도·안내 설정·화면 테마·계정 유형만 쓴다. consents 키는 서버 ConsentItem 이름이다. v0.11: theme(ScreenTheme),
 * accountType(GUEST·LINKED), linkedProvider(게스트면 null), battleSettings.proximityAlert(기본 참)를 더했다(D-90, D-92).
 */
@Serializable
internal data class MemberSettingsResponse(
    val unit: String? = null,
    val recordVisibility: String? = null,
    val courseMapShared: Boolean = false,
    val profileVisibility: String? = null,
    val notificationPrefs: Map<String, Boolean> = emptyMap(),
    val watchLinked: Boolean = false,
    val runSettings: RunSettingsDto? = null,
    val battleSettings: BattleSettingsDto? = null,
    val consents: Map<String, Boolean> = emptyMap(),
    val birthYear: Int? = null,
    val theme: String? = null,
    val accountType: String? = null,
    val linkedProvider: String? = null
) {
    /** 서버 member.RunSettings. */
    @Serializable
    data class RunSettingsDto(
        val target: String? = null,
        val voice: Boolean = true,
        val vibration: Boolean = true,
        val followCoachPlan: Boolean = false
    )

    /** 서버 member.BattleSettings. gapAlertInterval은 M500 또는 KM1, proximityAlert는 근접 알림(FR-196, 기본 참). */
    @Serializable
    data class BattleSettingsDto(
        val defaultDistance: String? = null,
        val voice: Boolean = true,
        val vibration: Boolean = true,
        val gapAlertInterval: String = "KM1",
        val proximityAlert: Boolean = true
    )
}
