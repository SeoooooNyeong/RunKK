package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.RunMode

/**
 * 진행값에 맞는 안내를 고른다(6.1.14). rules는 러닝 모드마다 조립한다:
 * 자유런 = Split·TargetReached·Pace·Breath, 대결 = Gap·Breath, 크루런 = Split·Breath(합산 방식, 순위 없음).
 */
class CoachingJudge(val rules: List<GuidanceRule>) {
    /** 규칙마다 check하고 null이 아닌 것만 모은다. */
    fun evaluate(ctx: GuidanceContext): List<Guidance> = rules.mapNotNull { it.check(ctx) }

    companion object {
        /** 러닝 한 회에 쓸 규칙 묶음을 새로 만든다(규칙이 그 회의 상태를 가진다). */
        fun forMode(mode: RunMode): CoachingJudge = CoachingJudge(
            when (mode) {
                RunMode.FREE ->
                    listOf(SplitGuidanceRule(), TargetReachedRule(), PaceGuidanceRule(), BreathGuidanceRule())

                RunMode.BATTLE -> listOf(GapGuidanceRule(), BreathGuidanceRule())

                RunMode.CREW_RUN -> listOf(SplitGuidanceRule(), BreathGuidanceRule())
            }
        )
    }
}
