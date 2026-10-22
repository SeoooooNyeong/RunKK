package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.settings.GuidanceSettings

/**
 * CoachingJudge.evaluate의 입력(design-review.md M24). RunTracker가 1초마다 만든다.
 * birthYear·guidanceSettings는 SettingsRepository에서 읽는다.
 */
data class GuidanceContext(
    val progress: RunProgress,
    val todayPlan: PlanSnapshot?,
    val gapMeters: Int?,
    val rank: CrewRank?,
    val birthYear: Int?,
    val guidanceSettings: GuidanceSettings
)
