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
