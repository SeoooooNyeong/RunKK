package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * 안내 하나의 판정 계약(6.1.14, 6.4 다형성). 러닝 한 회마다 CoachingJudge.forMode가 새로 만들고,
 * 구현은 "새 지점"·"한 번만" 같은 판정을 위해 그 회의 상태를 가진다.
 */
interface GuidanceRule {
    fun check(ctx: GuidanceContext): Guidance?
}

/** 새 1km 지점마다 구간 안내(FR-049). 음성·진동은 러닝 설정을 따른다. */
class SplitGuidanceRule : GuidanceRule {
    private val points = PointCrossing(KILOMETER)

    override fun check(ctx: GuidanceContext): Guidance? {
        val km = points.newPoint(ctx.progress.distance) ?: return null
        val settings = ctx.guidanceSettings
        return Guidance(Guidance.Kind.SPLIT, "${km}km 지점입니다", settings.runVoice, settings.runVibration)
    }
}

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

/**
 * 심박이 기준 이상으로 30초 이어지고 직전 안내 뒤 60초가 지났으면 호흡 안내(FR-045).
 * 기준 심박 = (220 − 나이) × 0.9, 출생연도가 없으면 180bpm. 심박이 없으면 판단하지 않는다.
 */
class BreathGuidanceRule : GuidanceRule {
    private var aboveSince: Instant? = null
    private var lastGuidedAt: Instant? = null

    override fun check(ctx: GuidanceContext): Guidance? {
        val heartRate = ctx.progress.heartRate
        if (heartRate == null || heartRate.bpm < thresholdOf(ctx.birthYear, heartRate.at)) {
            aboveSince = null
            return null
        }
        val since = aboveSince ?: heartRate.at.also { aboveSince = it }
        val sustained = Duration.between(since, heartRate.at) >= SUSTAIN
        val last = lastGuidedAt
        val rested = last == null || Duration.between(last, heartRate.at) >= INTERVAL
        if (!sustained || !rested) return null
        lastGuidedAt = heartRate.at
        val settings = ctx.guidanceSettings
        return Guidance(
            Guidance.Kind.BREATH,
            "심박이 높아요. 호흡을 고르세요 (의료 판단이 아닙니다)",
            settings.runVoice,
            settings.runVibration
        )
    }

    private fun thresholdOf(birthYear: Int?, at: Instant): Double {
        if (birthYear == null) return DEFAULT_THRESHOLD
        val age = at.atOffset(ZoneOffset.UTC).year - birthYear
        return (MAX_HEART_RATE_BASE - age) * THRESHOLD_RATIO
    }

    private companion object {
        val SUSTAIN: Duration = Duration.ofSeconds(30)
        val INTERVAL: Duration = Duration.ofSeconds(60)
        const val DEFAULT_THRESHOLD = 180.0
        const val MAX_HEART_RATE_BASE = 220
        const val THRESHOLD_RATIO = 0.9
    }
}

/**
 * 대결: 중간 알림 간격(500m/1km)의 새 지점에서 음성이 켜져 있으면 격차와 남은 거리(FR-189).
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
        val position = if (gap >= 0) "${gap}m 앞서는 중" else "${abs(gap)}m 뒤처져 있어요"
        val remaining = ctx.todayPlan?.targetDistance?.minus(ctx.progress.distance)
        val message = remaining?.let { "$position, 남은 거리 ${it.meters}m" } ?: position
        return Guidance(Guidance.Kind.GAP, message, voice = true, vibration = false)
    }
}

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

private const val KILOMETER = 1_000L

/** 누적 거리가 간격의 새 지점(1, 2, …번째)을 지났는지 센다. 여러 지점을 한 번에 넘으면 가장 큰 지점 하나만 알린다. */
private class PointCrossing(val intervalMeters: Long, startAt: Distance = Distance.ZERO) {
    private var lastPoint = startAt.meters / intervalMeters

    fun newPoint(distance: Distance): Long? {
        val point = distance.meters / intervalMeters
        if (point <= lastPoint) return null
        lastPoint = point
        return point
    }
}
