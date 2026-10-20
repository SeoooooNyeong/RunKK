package com.a207.runkk.app.domain.settings

/**
 * 안내 설정(6.1.14 SettingsSnapshot.guidanceSettings): 음성·진동, 중간 알림 간격, 순위 알림, 코치 계획 따르기.
 * 서버는 러닝 설정(RunSettings)과 대결 설정(BattleSettings)에 음성·진동을 따로 두므로 둘을 나눠 담는다(FR-048, FR-187).
 */
data class GuidanceSettings(
    val runVoice: Boolean,
    val runVibration: Boolean,
    val battleVoice: Boolean,
    val battleVibration: Boolean,
    val gapAlertMeters: Int,
    val rankAlert: Boolean,
    val followCoachPlan: Boolean
) {
    init {
        require(gapAlertMeters > 0) { "중간 알림 간격은 0보다 커야 한다: $gapAlertMeters" }
    }

    companion object {
        /** 서버 기본값(srs FR-048·FR-187·FR-190 제안): 음성·진동 켬, 중간 알림 1km, 순위 알림 켬, 코치 계획 따르기 꺼짐. */
        val DEFAULT =
            GuidanceSettings(
                runVoice = true,
                runVibration = true,
                battleVoice = true,
                battleVibration = true,
                gapAlertMeters = 1_000,
                rankAlert = true,
                followCoachPlan = false
            )
    }
}
