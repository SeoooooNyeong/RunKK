package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

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
            GuidanceMessage.BREATH_HIGH,
            hasVoice = settings.runVoice,
            hasVibration = settings.runVibration
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
