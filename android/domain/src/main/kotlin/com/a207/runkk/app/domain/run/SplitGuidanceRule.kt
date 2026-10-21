package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** 새 1km 지점마다 구간 안내(FR-049). 음성·진동은 러닝 설정을 따른다. */
class SplitGuidanceRule : GuidanceRule {
    private val points = PointCrossing(KILOMETER)

    override fun check(ctx: GuidanceContext): Guidance? {
        val km = points.newPoint(ctx.progress.distance) ?: return null
        val settings = ctx.guidanceSettings
        return Guidance(
            Guidance.Kind.SPLIT,
            GuidanceMessage.SPLIT,
            listOf(km),
            hasVoice = settings.runVoice,
            hasVibration = settings.runVibration
        )
    }
}
