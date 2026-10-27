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

// 실시간 메시지 전송 형식(5.7.2)과 도메인 변환. 항목 이름은 서버 battle.live·crew.live record·realtime.InboundProgress와 같고
// 계약 표본(src/test/resources/contract/realtime)으로 지킨다. 거리는 미터, 경과·기록 시간은 초, 시각은 ServerTime 형식이다.
// 이 형식은 data.live 밖으로 나가지 않는다.

internal val liveJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

internal fun ProgressReport.toJsonObject(): JsonObject = liveJson
    .encodeToJsonElement(
        InboundProgressJson.serializer(),
        InboundProgressJson(
            PROGRESS_TYPE,
            channel.name,
            channelId,
            distanceMeters,
            elapsedSeconds,
            ServerTime.formatWithOffset(sentAt)
        )
    ).jsonObject

internal fun ChatPresence.toJsonObject(): JsonObject = liveJson
    .encodeToJsonElement(ChatPresenceJson.serializer(), ChatPresenceJson(CHAT_PRESENCE_TYPE, crewId, isPresent))
    .jsonObject

/** 앱 → 서버 메시지를 type과 함께 JSON으로 바꾼다(F.3.2). */
internal fun ClientMessage.toJsonObject(): JsonObject = when (this) {
    is ProgressReport -> toJsonObject()
    is ChatPresence -> toJsonObject()
}

private const val PROGRESS_TYPE = "Progress"
private const val CHAT_PRESENCE_TYPE = "ChatPresence"
