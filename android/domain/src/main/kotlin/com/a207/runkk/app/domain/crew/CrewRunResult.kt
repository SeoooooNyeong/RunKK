package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 크루런 결과(서버 crew.CrewRunResultView, 합산 방식 S15P31A207-236): 크루 합산 거리와 참가자별 거리(대기실 입장 순).
 * [status]가 RUNNING이면 아직 달리는 사람이 있고, [myRecordId]가 null이면 내 기록이 아직 올라가지 않았다(화면이 다시 읽는다).
 */
data class CrewRunResult(
    val postId: Long,
    val participants: List<Participant>,
    val totalDistance: Distance,
    val isFirstCrewRun: Boolean = false,
    val raceContribution: Distance = Distance.ZERO,
    val status: CrewRunStatus? = null,
    val title: String = "",
    val distance: Distance? = null,
    val startedAt: Instant? = null,
    val myRecordId: String? = null
) {
    /** 아직 끝내지 않은 참가자가 있다(크루런 진행 중). */
    val isRunning: Boolean get() = status == CrewRunStatus.RUNNING

    /** [isMe]는 열람자 본인, [memberId]는 탈퇴했으면 null, [hasEnded]는 그만두기나 크루런 종료로 마감됐는가. */
    data class Participant(
        val displayName: String,
        val distance: Distance,
        val elapsedSeconds: Long? = null,
        val averagePaceSecondsPerKm: Int? = null,
        val hasEnded: Boolean = true,
        val memberId: Long? = null,
        val isMe: Boolean = false
    )
}
