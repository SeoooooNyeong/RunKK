package com.a207.runkk.app.data.live

import android.os.SystemClock
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.DateTimeException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * 서버 → 앱 실시간 메시지를 해독한다(6.1.14 data.live, 5.7.2). 서버 RealtimeMessageSerializer는 메시지 record의 type() 값을
 * type 항목에 쓰고 나머지 구성요소를 그대로 직렬화하므로(D-06), type으로 16종 가운데 하나를 고른다(채팅 3종 v0.11, F.3.2,
 * 친구 초대 끝 2종 v0.14 D-269).
 * 모르는 type, 깨진 JSON, 범위 밖 값(음수 거리, 모르는 상태 이름)은 null로 버린다. 연결은 끊지 않는다. 사유 항목(CrewChatRemoved.reason)이
 * 모르는 상수면 그 메시지를 버리고 경고 로그를 남긴다(D-85).
 * 크루런 중계의 본인 줄(CrewRunRelay.Entry.isMe)은 서버 mine이 있으면 그 값, 없으면 [selfId](접근 토큰의 회원 ID, SelfMember)와
 * memberId를 비교해 채운다.
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

                "MatchFailed" -> liveJson.decodeFromJsonElement(MatchFailedJson.serializer(), message).toDomain()

                "FriendInviteDeclined" -> liveJson.decodeFromJsonElement(
                    FriendInviteDeclinedJson.serializer(),
                    message
                ).toDomain()

                "FriendInviteExpired" -> liveJson.decodeFromJsonElement(
                    FriendInviteExpiredJson.serializer(),
                    message
                ).toDomain()

                "BattleStart" -> liveJson.decodeFromJsonElement(BattleStartJson.serializer(), message).toDomain()

                "OpponentProgress" -> liveJson.decodeFromJsonElement(
                    OpponentProgressJson.serializer(),
                    message
                ).toDomain().also(::logReceived)

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

                "CrewChatPosted" -> liveJson.decodeFromJsonElement(CrewChatPostedJson.serializer(), message).toDomain()

                "CrewChatRemoved" -> decodeRemoved(message)

                "CrewChatReactionChanged" -> liveJson.decodeFromJsonElement(
                    CrewChatReactionChangedJson.serializer(),
                    message
                ).toDomain()

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

    /**
     * [임시:NFR-성-7] 측정용 기록([임시:D-성-274]): 상대 진행값을 받은 시각(부팅 뒤 ms)과 거리값(상대 − 나, m)을 남긴다. 로그 나무는 디버그
     * 빌드에서만 심으므로(RunkkApp) 그 밖에서는 시계도 읽지 않는다. 그린 쪽(core.designsystem RunkkTrackCard)이 같은 태그로 남긴 시각과의
     * 차를 실기기에서 잰다.
     */
    private fun logReceived(progress: OpponentProgress) {
        if (Timber.treeCount == 0) return
        Timber.tag(TRACK_TIMING_LOG_TAG).d("recv ahead=%d at=%d", -progress.gapMeters, SystemClock.uptimeMillis())
    }

    /** 모르는 삭제 사유는 그 메시지만 버리고 로그를 남긴다(D-85, TC-6470). */
    private fun decodeRemoved(message: JsonObject): ServerMessage? {
        val removed = liveJson.decodeFromJsonElement(CrewChatRemovedJson.serializer(), message)
        if (CrewChatRemoved.Reason.entries.none { it.name == removed.reason }) {
            Timber.w("모르는 채팅 삭제 사유를 버린다: %s", removed.reason)
            return null
        }
        return removed.toDomain()
    }

    private companion object {
        const val TYPE = "type"

        /** core.designsystem의 TRACK_TIMING_LOG_TAG와 같은 글자다(data는 화면 모듈을 보지 않는다). */
        const val TRACK_TIMING_LOG_TAG = "RunkkNfr7"
    }
}
