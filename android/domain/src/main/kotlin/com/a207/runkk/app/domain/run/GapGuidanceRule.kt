package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * 대결: 중간 알림 간격(500m/1km)의 새 지점에서 음성이 켜져 있으면 격차와 남은 거리(FR-189). 진동은 대결 진동 설정을 따른다(F-2).
 * 남은 거리는 todayPlan.targetDistance(조정자가 넣은 대결 거리)가 있을 때만 붙인다.
 */
class GapGuidanceRule : GuidanceRule {
    private var crossing: PointCrossing? = null

    override fun check(ctx: GuidanceContext): Guidance? {
        val settings = ctx.guidanceSettings
        val interval = settings.gapAlertMeters.toLong()
        // 간격 설정이 바뀌면 이미 지난 지점을 다시 알리지 않도록 지금 거리를 기준점으로 새로 센다.
        val start = if (crossing == null) Distance.ZERO else ctx.progress.distance
        val points =
            crossing?.takeIf { it.intervalMeters == interval }
                ?: PointCrossing(interval, start).also { crossing = it }
        val newPoint = points.newPoint(ctx.progress.distance)
        val gap = ctx.gapMeters
        if (newPoint == null || gap == null || !settings.battleVoice) return null
        val isAhead = gap >= 0
        val remaining = ctx.todayPlan?.targetDistance?.minus(ctx.progress.distance)
        val message =
            when {
                remaining == null -> if (isAhead) GuidanceMessage.GAP_AHEAD else GuidanceMessage.GAP_BEHIND
                isAhead -> GuidanceMessage.GAP_AHEAD_REMAINING
                else -> GuidanceMessage.GAP_BEHIND_REMAINING
            }
        val args = listOfNotNull<Any>(abs(gap), remaining?.meters)
        return Guidance(Guidance.Kind.GAP, message, args, hasVoice = true, hasVibration = settings.battleVibration)
    }
}
