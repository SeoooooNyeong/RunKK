package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.DateTimeException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 서버 → 앱 실시간 메시지를 해독한다(6.1.14 data.live, 5.7.2). 서버 RealtimeMessageSerializer는 메시지 record의 type() 값을
 * type 항목에 쓰고 나머지 구성요소를 그대로 직렬화하므로(D-06), type으로 10종 가운데 하나를 고른다.
 * 모르는 type, 깨진 JSON, 범위 밖 값(음수 거리, 모르는 상태 이름)은 null로 버린다. 연결은 끊지 않는다.
 * 크루런 중계의 본인 줄(CrewRunRelay.Entry.me)은 [selfId](접근 토큰의 회원 ID, SelfMember)와 memberId를 비교해 채운다.
 */
class ServerMessageDecoder internal constructor(private val selfId: () -> Long? = { null }) {
    @Inject
    internal constructor(self: SelfMember) : this({ self.memberId() })

    fun decode(text: String): ServerMessage? {
        val element =
            try {
                liveJson.parseToJsonElement(text)
            } catch (e: SerializationException) {
                return null
            }
        return decode(element)
    }

    fun decode(element: JsonElement): ServerMessage? {
        val message = element as? JsonObject ?: return null
        val type = (message[TYPE] ?: return null).let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        return try {
            when (type) {
                "MatchFound" -> liveJson.decodeFromJsonElement(MatchFoundJson.serializer(), message).toDomain()

                "BattleStart" -> liveJson.decodeFromJsonElement(BattleStartJson.serializer(), message).toDomain()

                "OpponentProgress" -> liveJson.decodeFromJsonElement(
                    OpponentProgressJson.serializer(),
                    message
                ).toDomain()

                "BattleGuidance" -> liveJson.decodeFromJsonElement(BattleGuidanceJson.serializer(), message).toDomain()

                "CountdownStarted" -> liveJson.decodeFromJsonElement(
                    CountdownStartedJson.serializer(),
                    message
                ).toDomain()

                "BattleEnded" -> liveJson.decodeFromJsonElement(BattleEndedJson.serializer(), message).toDomain()

                "CrewRunStart" -> liveJson.decodeFromJsonElement(CrewRunStartJson.serializer(), message).toDomain()

                "CrewRunCancelled" -> liveJson.decodeFromJsonElement(
                    CrewRunCancelledJson.serializer(),
                    message
                ).toDomain()

                "CrewRunRelay" -> liveJson.decodeFromJsonElement(CrewRunRelayJson.serializer(), message).toDomain(
                    selfId()
                )

                "CrewRunEnded" -> liveJson.decodeFromJsonElement(CrewRunEndedJson.serializer(), message).toDomain()

                else -> null
            }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            // 범위 밖 값(Distance 음수), 모르는 enum 이름
            null
        } catch (e: DateTimeException) {
            // 시각 형식 오류
            null
        }
    }

    private companion object {
        const val TYPE = "type"
    }
}
