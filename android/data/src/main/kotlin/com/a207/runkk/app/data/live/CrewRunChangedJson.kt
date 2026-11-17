package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.domain.live.CrewRunChanged
import kotlinx.serialization.Serializable

/** 서버 crew.live.CrewRunChanged(설계 05-32 5.3). 모르는 자원 이름이면 null(메시지를 버린다). */
@Serializable
internal data class CrewRunChangedJson(
    val crewId: Long,
    val postId: Long,
    val resource: String,
    val revision: Long,
    val eventId: String,
    val occurredAt: String
) {
    fun toDomain(): CrewRunChanged? {
        val kind = CrewRunChanged.Resource.entries.firstOrNull { it.name == resource } ?: return null
        return CrewRunChanged(crewId, postId, kind, revision, eventId, ServerTime.parse(occurredAt))
    }
}
