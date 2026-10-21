package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** 코치 계획 따르기가 켜져 있고 목표 페이스가 있을 때, 새 1km 지점에서 평균이 목표의 1.1배 이상이면(FR-044). */
class PaceGuidanceRule : GuidanceRule {
    private val points = PointCrossing(KILOMETER)

    override fun check(ctx: GuidanceContext): Guidance? {
        val newPoint = points.newPoint(ctx.progress.distance)
        val settings = ctx.guidanceSettings
        if (newPoint == null || !settings.followCoachPlan) return null
        val target = ctx.todayPlan?.targetPace ?: return null
        val average = ctx.progress.averagePace ?: return null
        if (!average.isSlowerThanBy(target, SLOWER_RATIO)) return null
        return Guidance(Guidance.Kind.PACE, "목표 페이스보다 10% 넘게 느려요", settings.runVoice, settings.runVibration)
    }

    private companion object {
        const val SLOWER_RATIO = 0.1
    }
}
