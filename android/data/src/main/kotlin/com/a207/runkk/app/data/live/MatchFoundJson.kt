package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.RunnerLookJson
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
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

@Serializable
internal data class MatchFoundJson(
    val battleId: Long,
    val opponentName: String,
    val opponentTier: String,
    val distance: Long,
    val myBest: Long? = null,
    val opponentBest: Long? = null,
    val animationSeconds: Int,
    val opponentGender: String? = null,
    val placementRound: Int? = null,
    /** 상대 모습([임시:D-성-252]). 배치고사·고스트·가상 상대·옛 서버면 null이다. */
    val opponentLook: RunnerLookJson? = null
) {
    fun toDomain(): MatchFound = MatchFound(
        battleId = battleId,
        opponentName = opponentName,
        opponentTier = opponentTier,
        distance = Distance(distance),
        myBest = myBest?.let(Duration::ofSeconds),
        opponentBest = opponentBest?.let(Duration::ofSeconds),
        animationSeconds = animationSeconds,
        opponentGender = enumByName<Gender>(opponentGender),
        placementRound = placementRound,
        opponentLook = opponentLook?.toDomain()
    )
}
