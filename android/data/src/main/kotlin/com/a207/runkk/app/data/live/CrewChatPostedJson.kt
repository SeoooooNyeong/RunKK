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

/** 크루 채팅 새 메시지(서버 crew.chat.live.CrewChatPosted, F.3.2). authorId는 탈퇴한 작성자면 null이다. */
@Serializable
internal data class CrewChatPostedJson(
    val crewId: Long,
    val messageId: Long,
    val authorId: Long? = null,
    val authorName: String,
    val content: String,
    val sentAt: String,
    val clientMessageId: String? = null,
    val messageKind: String = "TEXT",
    val authorRole: String? = null,
    val runDistanceMeters: Long? = null
) {
    fun toDomain(): CrewChatPosted = CrewChatPosted(
        crewId,
        messageId,
        authorId,
        authorName,
        content,
        ServerTime.parse(sentAt),
        clientMessageId,
        messageKind,
        authorRole,
        runDistanceMeters
    )
}
