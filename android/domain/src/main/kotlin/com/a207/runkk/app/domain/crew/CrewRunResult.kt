package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루런 결과(서버 crew.CrewRunResultView, FR-191). */
data class CrewRunResult(
    val postId: Long,
    val finishers: List<Finisher>,
    val firstCrewRun: Boolean,
    val raceContribution: Distance
) {
    /** rank가 null이면 미완주. [quit]은 중간에 그만둔 사람, [me]는 열람자 본인, [memberId]는 탈퇴했으면 null. */
    data class Finisher(
        val rank: Int?,
        val displayName: String,
        val elapsedSeconds: Long?,
        val averagePaceSecondsPerKm: Int?,
        val quit: Boolean = false,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
