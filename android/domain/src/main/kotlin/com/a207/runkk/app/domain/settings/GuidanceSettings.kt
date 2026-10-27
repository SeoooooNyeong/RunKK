package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Distance

/**
 * 안내 설정(6.1.14 SettingsSnapshot.guidanceSettings): 음성·진동, 중간 알림 간격, 순위 알림, 코치 계획 따르기,
 * 자유런 목표 거리, 워치 근접 알림(서버 BattleSettings.proximityAlert, 기본 참, FR-196, D-92). 서버는 러닝 설정(RunSettings)과 대결 설정(BattleSettings)에 음성·진동을 따로 두므로 둘을 나눠 담는다
 * (FR-048, FR-187). freeRunTarget은 서버 RunSettings.target(FreeRunTarget)이고 FREE(목표 없음)면 null이다(FR-048).
 */
data class GuidanceSettings(
    val isRunVoiceOn: Boolean,
    val isRunVibrationOn: Boolean,
    val isBattleVoiceOn: Boolean,
    val isBattleVibrationOn: Boolean,
    val gapAlertMeters: Int,
    val isRankAlertOn: Boolean,
    val isFollowingCoachPlan: Boolean,
    val freeRunTarget: Distance? = null,
    val isProximityAlertOn: Boolean = true
) {
    init {
        require(gapAlertMeters > 0) { "gapAlertMeters must be positive: $gapAlertMeters" }
    }

    companion object {
        /**
         * 서버 기본값(srs FR-048·FR-187·FR-190 제안): 음성·진동 켬, 중간 알림 1km, 순위 알림 켬, 코치 계획 따르기 꺼짐,
         * 자유런 목표 자유(없음), 근접 알림 켬(D-162).
         */
        val DEFAULT =
            GuidanceSettings(
                isRunVoiceOn = true,
                isRunVibrationOn = true,
                isBattleVoiceOn = true,
                isBattleVibrationOn = true,
                gapAlertMeters = 1_000,
                isRankAlertOn = true,
                isFollowingCoachPlan = false,
                freeRunTarget = null,
                isProximityAlertOn = true
            )
    }
}
