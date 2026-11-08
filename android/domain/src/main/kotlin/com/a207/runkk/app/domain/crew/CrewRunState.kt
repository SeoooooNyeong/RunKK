package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.domain.live.CrewRunRelay
import java.time.Duration
import java.time.Instant

/** 크루런 진행 상태. CrewRunCoordinator.state로 ui.crew에 보인다. */
sealed interface CrewRunState {
    data object Idle : CrewRunState

    data class Lobby(val postId: Long) : CrewRunState

    data class Countdown(val postId: Long, val startAt: Instant) : CrewRunState

    /** 측정 중: 중계(CrewRunRelay)의 참가자 표·크루 합산 거리와 내 측정값([mine]). */
    data class Running(
        val postId: Long,
        val entries: List<CrewRunRelay.Entry>,
        val totalDistance: Distance = Distance.ZERO,
        val mine: CrewRunProgress = CrewRunProgress.ZERO
    ) : CrewRunState

    /** cancelledReason이 있으면 취소된 크루런이다. */
    data class Finished(val postId: Long, val cancelledReason: String?) : CrewRunState
}

/**
 * 크루런 화면에 보일 내 측정값(RunTracker 진행값에서 옮긴 값). ui.crew는 domain.run을 보지 않으므로 여기로 옮긴다(5.1.2).
 * [route]는 측정 중 경로선(RunTracker.routeLine, D-261)이다.
 */
data class CrewRunProgress(
    val distance: Distance,
    val elapsed: Duration,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val isPaused: Boolean = false,
    val route: RouteLine = RouteLine.EMPTY
) {
    companion object {
        val ZERO = CrewRunProgress(Distance.ZERO, Duration.ZERO, currentPace = null, averagePace = null)
    }
}
