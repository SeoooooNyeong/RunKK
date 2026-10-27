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

/** opponentTrack은 [{elapsedSeconds, distanceMeters}] 배열(기록 상대가 아니면 null). {"points": [...]} 모양도 받아들인다. */
@Serializable
internal data class BattleStartJson(val battleId: Long, val startAt: String, val opponentTrack: JsonElement? = null) {
    fun toDomain(): BattleStart = BattleStart(battleId, ServerTime.parse(startAt), opponentTrack?.toTrackTable())

    private fun JsonElement.toTrackTable(): TrackTable? {
        val points =
            when (this) {
                is JsonNull -> return null
                is JsonArray -> this
                is JsonObject -> this["points"] as? JsonArray ?: return null
                else -> return null
            }
        return TrackTable(
            points.map { liveJson.decodeFromJsonElement(TrackPointJson.serializer(), it) }
                .map { TrackPoint(it.elapsedSeconds, it.distanceMeters) }
        )
    }
}
