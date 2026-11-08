package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 크루런 중계(서버 crew.live.CrewRunRelay, 합산 방식). 본인 줄은 서버가 mine을 주면 그 값(D-161, Kotlin 이름 isMe)이고,
 * mine 없이 memberId만 주면 접근 토큰의 회원 ID와 같은 줄이다. 모르는 상태 값은 달리는 중으로 읽는다.
 */
@Serializable
internal data class CrewRunRelayJson(val postId: Long, val totalDistance: Long? = null, val entries: List<Entry>) {
    @Serializable
    data class Entry(
        val name: String,
        val distance: Long,
        val state: String,
        val memberId: Long? = null,
        @SerialName("mine") val isMe: Boolean? = null
    )

    /** [selfId]는 받는 사람 회원 ID(모르면 null). 합산 거리가 없으면 줄 거리의 합이다. */
    fun toDomain(selfId: Long?): CrewRunRelay = CrewRunRelay(
        postId,
        Distance(totalDistance ?: entries.sumOf { it.distance }),
        entries.map {
            CrewRunRelay.Entry(
                it.name,
                Distance(it.distance),
                CrewRunRelay.State.entries.firstOrNull { s -> s.name == it.state } ?: CrewRunRelay.State.RUNNING,
                memberId = it.memberId,
                isMe = it.isMe ?: (selfId != null && it.memberId == selfId)
            )
        }
    )
}
