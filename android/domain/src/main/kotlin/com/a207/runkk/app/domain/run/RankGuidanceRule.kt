package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** 크루런: 새 1km 지점에서 순위 알림이 켜져 있으면 "n명 중 n위"(FR-190). */
class RankGuidanceRule : GuidanceRule {
    private val points = PointCrossing(KILOMETER)

    override fun check(ctx: GuidanceContext): Guidance? {
        val newPoint = points.newPoint(ctx.progress.distance)
        val rank = ctx.rank
        if (newPoint == null || rank == null || !ctx.guidanceSettings.rankAlert) return null
        return Guidance(Guidance.Kind.RANK, "${rank.headcount}명 중 ${rank.rank}위", voice = true, vibration = false)
    }
}
