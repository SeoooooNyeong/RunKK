package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.domain.live.CrewRunDeparture
import kotlinx.serialization.Serializable

/** 서버 crew.live.CrewRunDeparture(설계 05-32 5.3). startAt은 측정 시작 절대시각이다. */
@Serializable
internal data class CrewRunDepartureJson(
    val crewId: Long,
    val postId: Long,
    val revision: Long,
    val countdownToken: String,
    val startAt: String,
    val participantIds: List<Long> = emptyList()
) {
    fun toDomain(): CrewRunDeparture =
        CrewRunDeparture(crewId, postId, revision, countdownToken, ServerTime.parse(startAt), participantIds)
}
