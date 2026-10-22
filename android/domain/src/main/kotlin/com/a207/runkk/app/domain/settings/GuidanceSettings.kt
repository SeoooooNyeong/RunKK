package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Distance

/**
 * 안내 설정(6.1.14 SettingsSnapshot.guidanceSettings): 음성·진동, 중간 알림 간격, 순위 알림, 코치 계획 따르기,
 * 자유런 목표 거리. 서버는 러닝 설정(RunSettings)과 대결 설정(BattleSettings)에 음성·진동을 따로 두므로 둘을 나눠 담는다
 * (FR-048, FR-187). freeRunTarget은 서버 RunSettings.target(FreeRunTarget)이고 FREE(목표 없음)면 null이다(FR-048).
 */
data class GuidanceSettings(
    val isRunVoiceOn: Boolean,
    val isRunVibrationOn: Boolean,
    val isBattleVoiceOn: Boolean,
    val isBattleVibrationOn: Boolean,
    val gapAlertMeters: Int,
    val isRankAlertOn: Boolean,
    val shouldFollowCoachPlan: Boolean,
    val freeRunTarget: Distance? = null
) {
    init {
        require(gapAlertMeters > 0) { "중간 알림 간격은 0보다 커야 한다: $gapAlertMeters" }
    }

    companion object {
        /**
         * 서버 기본값(srs FR-048·FR-187·FR-190 제안): 음성·진동 켬, 중간 알림 1km, 순위 알림 켬, 코치 계획 따르기 꺼짐,
         * 자유런 목표 자유(없음).
         */
        val DEFAULT =
            GuidanceSettings(
                isRunVoiceOn = true,
                isRunVibrationOn = true,
                isBattleVoiceOn = true,
                isBattleVibrationOn = true,
                gapAlertMeters = 1_000,
                isRankAlertOn = true,
                shouldFollowCoachPlan = false,
                freeRunTarget = null
            )
    }
}
