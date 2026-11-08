package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import java.time.Duration

/**
 * 잠금 화면 진행 알림에 싣는 값(D-258, FR-224). RunTracker가 1초마다 만들고 data.device가 문구로 바꿔 알림을 그린다.
 * 모드 분기는 [of] 한 곳뿐이다: 자유런은 [Free], 크루런은 [CrewRun](합산 방식, 피그마 F-15b), 그 밖(랜덤·친구·셀프·셀럽 대결)은
 * [Battle]이다.
 * [progressEnd]는 진행 막대의 끝 값(자유런 목표 거리·대결 거리)이고 null이면 막대를 그리지 않는다.
 */
sealed interface ProgressNotice {
    val elapsed: Duration
    val distance: Distance
    val progressEnd: Distance?

    data class Free(
        override val elapsed: Duration,
        override val distance: Distance,
        val goal: FreeRunGoalState,
        val isPaused: Boolean
    ) : ProgressNotice {
        override val progressEnd: Distance?
            get() = when (goal) {
                FreeRunGoalState.None -> null
                FreeRunGoalState.Reached -> distance
                is FreeRunGoalState.Remaining -> distance.plus(goal.distance)
            }
    }

    /** 크루런 알림(F-15b): 경과·내 거리와 일시정지·재개 단추. 각자 달리는 거리라 막대가 없다. */
    data class CrewRun(override val elapsed: Duration, override val distance: Distance, val isPaused: Boolean) :
        ProgressNotice {
        override val progressEnd: Distance?
            get() = null
    }

    /**
     * 대결 알림. [gapMeters]는 앞서면 양수이고 모르면(연결 확인 중) null이다. [battleDistance]는 대결 거리다.
     * [battleId]는 알림을 눌렀을 때 돌아갈 대결이고 모르면 null이다(S15P31A207-278).
     */
    data class Battle(
        val opponentName: String,
        override val elapsed: Duration,
        override val distance: Distance,
        val gapMeters: Int?,
        val battleDistance: Distance? = null,
        val battleId: Long? = null
    ) : ProgressNotice {
        override val progressEnd: Distance?
            get() = battleDistance
    }

    companion object {
        fun of(spec: RunStartSpec, progress: RunProgress, gapMeters: Int?): ProgressNotice {
            val target = spec.todayPlan?.targetDistance
            return when (spec.cardKind) {
                CardKind.FREE_RUN -> Free(
                    progress.elapsed,
                    progress.distance,
                    FreeRunGoalState.of(target, progress.distance),
                    progress.isPaused
                )

                CardKind.CREW_RUN -> CrewRun(progress.elapsed, progress.distance, progress.isPaused)

                else -> Battle(
                    spec.opponentName.orEmpty(),
                    progress.elapsed,
                    progress.distance,
                    gapMeters,
                    target,
                    spec.battleId
                )
            }
        }
    }
}
