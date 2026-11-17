package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.CrewRunRelay
import java.time.Instant

/** 크루런 진행 상태. CrewRunCoordinator.state로 ui.crew에 보인다. */
sealed interface CrewRunState {
    data object Idle : CrewRunState

    data class Lobby(val postId: Long) : CrewRunState

    /** 출발 예약의 고정 후보: [startAt](측정 시작 절대시각)까지 센다. */
    data class Countdown(val postId: Long, val startAt: Instant) : CrewRunState

    /**
     * 출발했지만 나는 진행 참가자가 아니다: 「함께 뛰기」 또는 「나가기」를 고른다(설계 05-32 [임시:D-성-883]). [departedCount]는 출발한
     * 확정 후보 수다(시트 문구).
     */
    data class JoinChoice(val postId: Long, val departedCount: Int = 0) : CrewRunState

    /** 측정 중: 중계(CrewRunRelay)의 참가자 표·크루 합산 거리와 내 측정값([mine]). */
    data class Running(
        val postId: Long,
        val entries: List<CrewRunRelay.Entry>,
        val totalDistance: Distance = Distance.ZERO,
        val mine: CrewRunProgress = CrewRunProgress.ZERO
    ) : CrewRunState

    /**
     * cancelledReason이 있으면 취소된 크루런이다. [recordId]는 이 회에서 저장을 맡긴 내 기록 ID이고 기록이 없으면(측정 전 끝·기록 미생성)
     * null이다. 결과 「확인」이 이 기록의 저장·업로드만 기다린다(설계 05-32 [임시:D-성-890]).
     */
    data class Finished(val postId: Long, val cancelledReason: String?, val recordId: String? = null) : CrewRunState
}
