package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** 목표 거리에 처음 닿을 때 한 번(FR-049). */
class TargetReachedRule : GuidanceRule {
    private var reached = false

    override fun check(ctx: GuidanceContext): Guidance? {
        val target = ctx.todayPlan?.targetDistance ?: return null
        if (reached || !ctx.progress.distance.isAtLeast(target)) return null
        reached = true
        val settings = ctx.guidanceSettings
        return Guidance(
            Guidance.Kind.TARGET_REACHED,
            "목표 거리 ${target.km()}km에 도달했어요",
            settings.runVoice,
            settings.runVibration
        )
    }
}
