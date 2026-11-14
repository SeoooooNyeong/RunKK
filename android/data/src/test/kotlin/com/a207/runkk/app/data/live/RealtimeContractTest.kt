package com.a207.runkk.app.data.live

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 실시간 메시지 계약 시험(14장, 5.7.2, design-review.md H-17). 두 방향을 고정 표본으로 확인한다.
 *
 * 표본은 src/test/resources/contract/realtime/ 에 있고 백엔드 계약 시험이 같은 파일을 쓴다(한쪽만 고치지 않는다).
 * - 서버 → 앱 12종(<type>.json, BattleStart-random.json, v0.14 친구 초대 2종 포함): 서버 RealtimeMessageSerializer가 battle.live·crew.live record를
 *   직렬화한 결과와 JSON이 같아야 하고(서버 시험), 앱 ServerMessageDecoder가 아래 기대 도메인 값으로 읽어야 한다(이 시험).
 * - 앱 → 서버(inbound-progress-offset.json): 앱 ProgressReport 직렬화가 이 JSON과 같아야 하고(이 시험), 서버 realtime.InboundProgress가
 *   이 JSON을 읽어야 한다(서버 시험). inbound-progress.json은 오프셋 없는 옛 형식(LocalDateTime)으로, 서버가 계속 받는 호환 표본이라
 *   앱은 더 이상 이 형식으로 보내지 않는다(TC-6465).
 * 형식: type은 record 이름, 거리는 미터(정수), 경과·기록 시간은 초(정수), 서버가 보내는 시각은 Asia/Seoul의 ISO-8601 LocalDateTime(오프셋 없음),
 * 앱이 보내는 진행값 sentAt은 오프셋 포함(+09:00)이다.
 * opponentTrack은 [{elapsedSeconds, distanceMeters}] 배열이고 기록 상대가 아니면 null이다. 어떤 메시지에도 위도·경도·코스가 없다.
 */
class RealtimeContractTest {
    private val decoder = ServerMessageDecoder()

    @Test
    fun `서버 메시지 표본 13종을 해독한다(TC-9687 친구 초대 2종 포함)`() {
        val expected: Map<String, ServerMessage> =
            mapOf(
                "MatchFound" to
                    MatchFound(
                        battleId = 101,
                        opponentName = "바람처럼",
                        opponentTier = "GOLD",
                        distance = Distance(5_000),
                        myBest = Duration.ofSeconds(1_625),
                        opponentBest = Duration.ofSeconds(1_580),
                        animationSeconds = 3,
                        opponentGender = Gender.MALE,
                        opponentLook = SAMPLE_LOOK
                    ),
                "BattleStart" to
                    BattleStart(
                        battleId = 101,
                        startAt = utc("2026-10-20T10:00:13Z"),
                        opponentTrack =
                            TrackTable(
                                listOf(
                                    TrackPoint(0, 0),
                                    TrackPoint(60, 190),
                                    TrackPoint(120, 385),
                                    TrackPoint(1_580, 5_000)
                                )
                            )
                    ),
                "BattleStart-random" to BattleStart(102, utc("2026-10-20T10:05:13.500Z"), opponentTrack = null),
                "OpponentProgress" to
                    OpponentProgress(
                        battleId = 101,
                        opponentName = "바람처럼",
                        opponentDistance = Distance(1_240),
                        opponentElapsed = Duration.ofSeconds(402),
                        gapMeters = -35,
                        connection = OpponentProgress.Connection.CHECKING
                    ),
                "BattleGuidance" to BattleGuidance(101, BattleGuidance.Kind.OVERTOOK, 12),
                "CountdownStarted" to CountdownStarted(101, "바람처럼", utc("2026-10-20T10:27:13Z")),
                "BattleEnded" to BattleEnded(101, BattleEnded.Status.OPPONENT_FORFEITED),
                "CrewRunStart" to CrewRunStart(77, utc("2026-10-20T21:00:00Z"), listOf("새벽곰", "달리는여우", "한강토끼")),
                "CrewRunCancelled" to CrewRunCancelled(77, "참가자 부족"),
                "CrewRunRelay" to
                    CrewRunRelay(
                        77,
                        Distance(7_600),
                        listOf(
                            CrewRunRelay.Entry(
                                "새벽곰",
                                Distance(3_120),
                                CrewRunRelay.State.RUNNING,
                                11,
                                isMe = true
                            ),
                            CrewRunRelay.Entry("달리는여우", Distance(2_980), CrewRunRelay.State.PAUSED, 12),
                            CrewRunRelay.Entry("한강토끼", Distance(1_500), CrewRunRelay.State.ENDED, 13)
                        )
                    ),
                "CrewRunEnded" to CrewRunEnded(77),
                "MatchFailed" to MatchFailed("MATCH_FAILED", "대결을 만들지 못했어요. 다시 시도해 주세요"),
                "FriendInviteDeclined" to FriendInviteDeclined(41),
                "FriendInviteExpired" to FriendInviteExpired(42)
            )

        expected.forEach { (sample, message) ->
            assertEquals(message, decoder.decode(read("$sample.json")), "표본 $sample")
        }
        val types = expected.values.map { it::class.simpleName }.toSet()
        assertEquals(13, types.size, "서버 → 앱 메시지 13종을 모두 덮는다")
    }

    @Test
    fun `크루런 중계의 본인 줄은 서버 mine을 따르고 mine이 없을 때만 memberId와 접근 토큰 회원 ID를 비교한다`() {
        // 표본은 받는 사람 11 기준으로 11만 mine true이다. mine이 있으면 selfId와 무관하게 mine이 이긴다(D-161, A2 결정 6).
        val withSelf = ServerMessageDecoder { 11L }.decode(read("CrewRunRelay.json")) as CrewRunRelay
        val otherSelf = ServerMessageDecoder { 12L }.decode(read("CrewRunRelay.json")) as CrewRunRelay
        val unknown = ServerMessageDecoder { null }.decode(read("CrewRunRelay.json")) as CrewRunRelay

        assertEquals(listOf(true, false, false), withSelf.entries.map { it.isMe })
        assertEquals(listOf(true, false, false), otherSelf.entries.map { it.isMe }, "mine이 memberId 비교보다 우선")
        assertEquals(listOf(true, false, false), unknown.entries.map { it.isMe }, "회원 ID를 몰라도 mine을 따른다")
    }

    @Test
    fun `mine 없는 크루런 중계는 memberId와 접근 토큰 회원 ID가 같은 줄을 본인으로 보고 합산 거리가 없으면 줄 거리를 더한다`() {
        val legacy =
            """{"type":"CrewRunRelay","postId":77,"entries":[
              |{"name":"a","distance":3,"state":"RUNNING","memberId":11},
              |{"name":"b","distance":2,"state":"CHECKING","memberId":12},
              |{"name":"c","distance":1,"state":"SOMETHING_NEW"}]}
            """.trimMargin()
        val decoded = ServerMessageDecoder { 12L }.decode(legacy) as CrewRunRelay
        assertEquals(Distance(6), decoded.totalDistance)
        assertEquals(CrewRunRelay.State.RUNNING, decoded.entries[2].state, "모르는 상태는 달리는 중")

        assertEquals(
            listOf(false, true, false),
            (
                ServerMessageDecoder {
                    12L
                }.decode(legacy) as CrewRunRelay
                ).entries.map { it.isMe }
        )
        assertEquals(
            listOf(false, false, false),
            (
                ServerMessageDecoder {
                    null
                }.decode(legacy) as CrewRunRelay
                ).entries.map { it.isMe }
        )
    }

    @Test
    fun `TC-6465 앱 ProgressReport 직렬화는 서버 InboundProgress 오프셋 표본과 같다`() {
        val report =
            ProgressReport(
                channel = ProgressReport.Channel.BATTLE,
                channelId = 7,
                distanceMeters = 35,
                elapsedSeconds = 11,
                sentAt = utc("2026-10-22T10:00:11Z")
            )

        val encoded = report.toJsonObject()

        assertEquals("Progress", encoded["type"]?.let { (it as JsonPrimitive).content }, "새 앱은 type을 함께 보낸다(F.3.2)")
        assertEquals(Json.parseToJsonElement(read("inbound-progress-offset.json")), encoded)
    }

    @Test
    fun `TC-6860 앱 ChatPresence 직렬화는 inbound-chat-presence 표본과 같다`() {
        val encoded = ChatPresence(crewId = 7, isPresent = true).toJsonObject()

        assertEquals(Json.parseToJsonElement(read("inbound-chat-presence.json")), encoded)
    }

    @Test
    fun `TC-6469·TC-6859 채팅 메시지 3종 서버 표본을 해독한다`() {
        assertEquals(
            CrewChatPosted(
                7,
                801,
                3,
                "새벽곰",
                "내일 7시 출발 🏃",
                Instant.parse("2026-10-22T10:07:03Z"),
                "0f8fad5b-d9cb-469f-a165-70867728950e",
                messageKind = "TEXT",
                authorRole = "LEADER"
            ),
            decoder.decode(read("CrewChatPosted.json"))
        )
        assertEquals(
            CrewChatPosted(
                7,
                802,
                null,
                "탈퇴한 사용자",
                "먼저 갈게요",
                Instant.parse("2026-10-22T10:07:03.500Z"),
                "7c9e6679-7425-40de-944b-e07fc1f90ae7"
            ),
            decoder.decode(read("CrewChatPosted-withdrawn.json")),
            "탈퇴한 작성자는 authorId null, 밀리초 시각"
        )
        assertEquals(
            CrewChatRemoved(7, 801, CrewChatRemoved.Reason.DELETED),
            decoder.decode(read("CrewChatRemoved.json"))
        )
        assertEquals(
            CrewChatRemoved(7, 801, CrewChatRemoved.Reason.HIDDEN),
            decoder.decode(read("CrewChatRemoved-hidden.json"))
        )
        assertEquals(
            CrewChatPosted(
                7,
                803,
                4,
                "느긋한고래",
                "느긋한고래님이 5.02km를 달렸어요",
                Instant.parse("2026-10-22T10:40:00Z"),
                "9b2f4c3e-6a1d-3c5b-8e7f-0a1b2c3d4e5f",
                messageKind = "RUN_FINISHED",
                authorRole = "MEMBER",
                runDistanceMeters = 5_021
            ),
            decoder.decode(read("CrewChatPosted-runFinished.json")),
            "W5 러닝 완료 시스템 메시지는 종류·직책·거리를 싣는다"
        )
        assertEquals(
            CrewChatReactionChanged(7, 801, mapOf("LIKE" to 2, "HEART" to 1, "CHECK" to 3)),
            decoder.decode(read("CrewChatReactionChanged.json"))
        )
        assertEquals(
            CrewChatReactionChanged(7, 801, mapOf("LIKE" to 2)),
            decoder.decode("""{"type":"CrewChatReactionChanged","crewId":7,"messageId":801,"reactions":{"LIKE":2}}"""),
            "0인 종류는 키가 없다"
        )
    }

    @Test
    fun `TC-6470 모르는 삭제 사유의 채팅 메시지는 버린다`() {
        assertNull(decoder.decode("""{"type":"CrewChatRemoved","crewId":5,"messageId":101,"reason":"BANNED"}"""))
        assertNull(decoder.decode("""{"type":"CrewChatRemoved","crewId":5,"messageId":101,"reason":"ARCHIVED"}"""))
    }

    @Test
    fun `크루런 중계에 서버 mine이 있으면 그 값으로 본인 줄을 찾는다(D-161)`() {
        val relay =
            ServerMessageDecoder { 11L }.decode(
                """{"type":"CrewRunRelay","postId":1,"entries":[
                  |{"name":"a","distance":1,"state":"RUNNING","memberId":11,"mine":false},
                  |{"name":"b","distance":2,"state":"RUNNING","memberId":12,"mine":true}]}
                """.trimMargin()
            ) as CrewRunRelay

        assertEquals(listOf(false, true), relay.entries.map { it.isMe })
    }

    @Test
    fun `어떤 표본에도 위치 필드가 없다(FR-089, NFR-021)`() {
        val forbidden = setOf("latitude", "longitude", "lat", "lng", "lon", "course", "route", "points")
        SAMPLES.forEach { sample ->
            val keys = keysOf(Json.parseToJsonElement(read(sample)))
            assertFalse(keys.any { it.lowercase() in forbidden }, "$sample 에 위치 필드: $keys")
        }
    }

    @Test
    fun `모르는 종류·깨진 JSON·잘못된 값은 버린다`() {
        assertNull(decoder.decode("""{"type":"Unknown","battleId":1}"""))
        assertNull(decoder.decode("""{"battleId":1}"""))
        assertNull(decoder.decode("not json"))
        assertNull(decoder.decode("""{"type":"BattleEnded","battleId":1,"status":"EXPLODED"}"""))
        assertNull(
            decoder.decode(
                """{"type":"OpponentProgress","battleId":1,"opponentName":"x","opponentDistance":-1,"opponentElapsed":1,"gapMeters":0,"connection":"CONNECTED"}"""
            )
        )
    }

    @Test
    fun `기록이 없는 매칭 상대와 모르는 항목을 받아들인다`() {
        val message =
            decoder.decode(
                """{"type":"MatchFound","battleId":1,"opponentName":"고스트","opponentTier":"SILVER","distance":3000,
                  |"myBest":null,"animationSeconds":3,"addedLater":true}
                """.trimMargin()
            )

        assertEquals(MatchFound(1, "고스트", "SILVER", Distance(3_000), null, null, 3), message)
    }

    @Test
    fun `TC-5871 MatchFound의 배치고사 판 번호를 읽고 없으면 null이다`() {
        val placement = decoder.decode(read("MatchFound-placement.json")) as MatchFound
        val plain = decoder.decode(read("MatchFound.json")) as MatchFound
        val old = decoder.decode(
            """{"type":"MatchFound","battleId":1,"opponentName":"고스트","opponentTier":"SILVER","distance":3000,"animationSeconds":3}"""
        ) as MatchFound

        assertEquals(
            MatchFound(
                battleId = 103,
                opponentName = "느긋한고래",
                opponentTier = "UNRANKED",
                distance = Distance(5_000),
                myBest = null,
                opponentBest = Duration.ofSeconds(2_010),
                animationSeconds = 3,
                opponentGender = Gender.FEMALE,
                placementRound = 1
            ),
            placement
        )
        assertNull(plain.placementRound)
        assertNull(old.placementRound, "항목이 없는 옛 서버")
    }

    @Test
    fun `MatchFound에서 opponentLook을 읽는다`() {
        val plain = decoder.decode(read("MatchFound.json")) as MatchFound
        val placement = decoder.decode(read("MatchFound-placement.json")) as MatchFound
        val old = decoder.decode(
            """{"type":"MatchFound","battleId":1,"opponentName":"고스트","opponentTier":"SILVER","distance":3000,"animationSeconds":3}"""
        ) as MatchFound
        val bare = decoder.decode(
            """{"type":"MatchFound","battleId":1,"opponentName":"a","opponentTier":"SILVER","distance":3000,"animationSeconds":3,
              |"opponentLook":{"gender":"FEMALE","equipped":{},"bodyVariant":0,"runAmount":0,"faceKey":null}}
            """.trimMargin()
        ) as MatchFound

        assertEquals(SAMPLE_LOOK, plain.opponentLook)
        assertNull(placement.opponentLook, "배치고사 상대는 모습이 없다")
        assertNull(old.opponentLook, "항목이 없는 옛 서버")
        assertEquals(RunnerLook(Gender.FEMALE, null, null, null, 0, 0, null), bare.opponentLook, "안 입은 부위는 키가 없다")
    }

    @Test
    fun `모르는 키가 있어도 읽는다`() {
        val message = decoder.decode(
            """{"type":"MatchFound","battleId":1,"opponentName":"a","opponentTier":"SILVER","distance":3000,"animationSeconds":3,
              |"addedLater":1,"opponentLook":{"gender":"MALE","equipped":{"TOP":12,"HAT":99},"bodyVariant":2,"runAmount":7,
              |"faceKey":"head:1:x","hairColor":"brown"}}
            """.trimMargin()
        ) as MatchFound

        assertEquals(RunnerLook(Gender.MALE, 12, null, null, 2, 7, "head:1:x"), message.opponentLook)
    }

    @Test
    fun `모르는 성별의 모습은 없는 것으로 읽는다`() {
        val message = decoder.decode(
            """{"type":"MatchFound","battleId":1,"opponentName":"a","opponentTier":"SILVER","distance":3000,"animationSeconds":3,
              |"opponentLook":{"gender":"OTHER","equipped":{},"bodyVariant":0,"runAmount":0,"faceKey":null}}
            """.trimMargin()
        ) as MatchFound

        assertNull(message.opponentLook)
    }

    @Test
    fun `오프셋이 붙은 시각도 읽는다`() {
        val message = decoder.decode(
            """{"type":"CountdownStarted","battleId":1,"arrivedName":"a","endsAt":"2026-10-20T10:27:13Z"}"""
        )

        assertEquals(CountdownStarted(1, "a", Instant.parse("2026-10-20T10:27:13Z")), message)
    }

    private fun utc(text: String): Instant = Instant.parse(text)

    private fun read(name: String): String =
        requireNotNull(javaClass.getResource("/contract/realtime/$name")) { "표본 없음: $name" }.readText()

    private fun keysOf(element: JsonElement): Set<String> = when (element) {
        is JsonObject -> element.keys + element.values.flatMap(::keysOf)
        is JsonArray -> element.flatMap(::keysOf).toSet()
        else -> emptySet()
    }

    private companion object {
        val SAMPLE_LOOK =
            RunnerLook(
                Gender.MALE,
                topItemId = 12,
                bottomItemId = null,
                shoesItemId = 31,
                bodyVariant = 1,
                runAmount = 42,
                faceKey = "head:7:ab12"
            )

        val SAMPLES =
            listOf(
                "MatchFound.json",
                "MatchFound-placement.json",
                "BattleStart.json",
                "BattleStart-random.json",
                "OpponentProgress.json",
                "BattleGuidance.json",
                "CountdownStarted.json",
                "BattleEnded.json",
                "CrewRunStart.json",
                "CrewRunCancelled.json",
                "CrewRunRelay.json",
                "CrewRunEnded.json",
                "MatchFailed.json",
                "FriendInviteDeclined.json",
                "FriendInviteExpired.json",
                "inbound-progress.json",
                "inbound-progress-offset.json",
                "CrewChatPosted.json",
                "CrewChatPosted-withdrawn.json",
                "CrewChatPosted-runFinished.json",
                "CrewChatRemoved.json",
                "CrewChatRemoved-hidden.json",
                "CrewChatReactionChanged.json",
                "inbound-chat-presence.json"
            )
    }
}
