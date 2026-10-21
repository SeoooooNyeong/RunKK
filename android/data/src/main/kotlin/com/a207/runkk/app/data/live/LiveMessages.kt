package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
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

/** 앱 → 서버 진행값(서버 realtime.InboundProgress). 위치 필드가 없다(FR-089). */
@Serializable
internal data class InboundProgressJson(
    val channel: String,
    val channelId: Long,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val sentAt: String
)

internal fun ProgressReport.toJsonObject(): JsonObject = liveJson
    .encodeToJsonElement(
        InboundProgressJson.serializer(),
        InboundProgressJson(channel.name, channelId, distanceMeters, elapsedSeconds, ServerTime.format(sentAt))
    ).jsonObject

@Serializable
internal data class MatchFoundJson(
    val battleId: Long,
    val opponentName: String,
    val opponentTier: String,
    val distance: Long,
    val myBest: Long? = null,
    val opponentBest: Long? = null,
    val animationSeconds: Int
) {
    fun toDomain(): MatchFound = MatchFound(
        battleId = battleId,
        opponentName = opponentName,
        opponentTier = opponentTier,
        distance = Distance(distance),
        myBest = myBest?.let(Duration::ofSeconds),
        opponentBest = opponentBest?.let(Duration::ofSeconds),
        animationSeconds = animationSeconds
    )
}

/** 매칭 실패(서버 battle.live.MatchFailed). 문구가 비어 있으면 사유 코드 문구를 앱이 고른다. */
@Serializable
internal data class MatchFailedJson(val code: String, val message: String = "") {
    fun toDomain(): MatchFailed = MatchFailed(code, message)
}

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

@Serializable
internal data class TrackPointJson(val elapsedSeconds: Long, val distanceMeters: Long)

@Serializable
internal data class OpponentProgressJson(
    val battleId: Long,
    val opponentName: String,
    val opponentDistance: Long,
    val opponentElapsed: Long,
    val gapMeters: Int,
    val connection: String
) {
    fun toDomain(): OpponentProgress = OpponentProgress(
        battleId = battleId,
        opponentName = opponentName,
        opponentDistance = Distance(opponentDistance),
        opponentElapsed = Duration.ofSeconds(opponentElapsed),
        gapMeters = gapMeters,
        connection = OpponentProgress.Connection.valueOf(connection)
    )
}

@Serializable
internal data class BattleGuidanceJson(val battleId: Long, val kind: String, val gapMeters: Int) {
    fun toDomain(): BattleGuidance = BattleGuidance(battleId, BattleGuidance.Kind.valueOf(kind), gapMeters)
}

@Serializable
internal data class CountdownStartedJson(val battleId: Long, val arrivedName: String, val endsAt: String) {
    fun toDomain(): CountdownStarted = CountdownStarted(battleId, arrivedName, ServerTime.parse(endsAt))
}

@Serializable
internal data class BattleEndedJson(val battleId: Long, val status: String) {
    fun toDomain(): BattleEnded = BattleEnded(battleId, BattleEnded.Status.valueOf(status))
}

@Serializable
internal data class CrewRunStartJson(val postId: Long, val startAt: String, val participantNames: List<String>) {
    fun toDomain(): CrewRunStart = CrewRunStart(postId, ServerTime.parse(startAt), participantNames)
}

@Serializable
internal data class CrewRunCancelledJson(val postId: Long, val reason: String) {
    fun toDomain(): CrewRunCancelled = CrewRunCancelled(postId, reason)
}

@Serializable
internal data class CrewRunRelayJson(val postId: Long, val entries: List<Entry>) {
    @Serializable
    data class Entry(
        val name: String,
        val distance: Long,
        val rank: Int? = null,
        val connection: String,
        val memberId: Long? = null
    )

    /** [selfId]는 받는 사람 회원 ID(모르면 null). memberId가 같은 줄이 본인 줄이다. */
    fun toDomain(selfId: Long?): CrewRunRelay = CrewRunRelay(
        postId,
        entries.map {
            CrewRunRelay.Entry(
                it.name,
                Distance(it.distance),
                it.rank,
                CrewRunRelay.Connection.valueOf(it.connection),
                memberId = it.memberId,
                isMe = selfId != null && it.memberId == selfId
            )
        }
    )
}

@Serializable
internal data class CrewRunEndedJson(val postId: Long) {
    fun toDomain(): CrewRunEnded = CrewRunEnded(postId)
}
