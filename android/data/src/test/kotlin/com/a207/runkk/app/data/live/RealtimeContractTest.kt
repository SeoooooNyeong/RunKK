package com.a207.runkk.app.data.live

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
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

/**
 * 실시간 메시지 계약 시험(14장, 5.7.2, design-review.md H-17). 두 방향을 고정 표본으로 확인한다.
 *
 * 표본은 src/test/resources/contract/realtime/ 에 있고 백엔드 계약 시험이 같은 파일을 쓴다(한쪽만 고치지 않는다).
 * - 서버 → 앱 10종(<type>.json, BattleStart-random.json): 서버 RealtimeMessageSerializer가 battle.live·crew.live record를
 *   직렬화한 결과와 JSON이 같아야 하고(서버 시험), 앱 ServerMessageDecoder가 아래 기대 도메인 값으로 읽어야 한다(이 시험).
 * - 앱 → 서버(inbound-progress.json): 앱 ProgressReport 직렬화가 이 JSON과 같아야 하고(이 시험), 서버 realtime.InboundProgress가
 *   이 JSON을 읽어야 한다(서버 시험).
 * 형식: type은 record 이름, 거리는 미터(정수), 경과·기록 시간은 초(정수), 시각은 Asia/Seoul의 ISO-8601 LocalDateTime(오프셋 없음).
 * opponentTrack은 [{elapsedSeconds, distanceMeters}] 배열이고 기록 상대가 아니면 null이다. 어떤 메시지에도 위도·경도·코스가 없다.
 */
class RealtimeContractTest {
    private val decoder = ServerMessageDecoder()

    @Test
    fun `서버 메시지 표본 10종을 해독한다`() {
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
                        animationSeconds = 3
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
                        listOf(
                            CrewRunRelay.Entry("새벽곰", Distance(3_120), 1, CrewRunRelay.Connection.CONNECTED),
                            CrewRunRelay.Entry("달리는여우", Distance(2_980), 2, CrewRunRelay.Connection.CHECKING),
                            CrewRunRelay.Entry("한강토끼", Distance(1_500), null, CrewRunRelay.Connection.QUIT)
                        )
                    ),
                "CrewRunEnded" to CrewRunEnded(77)
            )

        expected.forEach { (sample, message) ->
            assertEquals(message, decoder.decode(read("$sample.json")), "표본 $sample")
        }
        val types = expected.values.map { it::class.simpleName }.toSet()
        assertEquals(10, types.size, "서버 → 앱 메시지 10종을 모두 덮는다")
    }

    @Test
    fun `앱 ProgressReport 직렬화는 서버 InboundProgress 표본과 같다`() {
        val report =
            ProgressReport(
                channel = ProgressReport.Channel.BATTLE,
                channelId = 101,
                distanceMeters = 1_275,
                elapsedSeconds = 410,
                sentAt = utc("2026-10-20T10:07:03.250Z")
            )

        val encoded = report.toJsonObject()

        assertEquals(Json.parseToJsonElement(read("inbound-progress.json")), encoded)
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
        val SAMPLES =
            listOf(
                "MatchFound.json",
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
                "inbound-progress.json"
            )
    }
}
