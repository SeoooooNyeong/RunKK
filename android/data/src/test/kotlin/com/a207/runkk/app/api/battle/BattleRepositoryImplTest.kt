package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.battle 매퍼(14장 단위(앱 데이터)): 매칭·후보·기록 대결 시작(상대 표)·이력 DTO → 도메인. */
class BattleRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = BattleRepositoryImpl(api.retrofit.create())

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `매칭 요청은 거리를 미터로 보내고 대기 여부를 읽는다`() = runTest {
        api.reply("""{"waiting":true,"distanceMeters":5000}""")

        assertEquals(Outcome.Success(MatchAcceptance(true, Distance(5_000))), repository.requestMatch(Distance(5_000)))
        assertEquals("""{"distanceMeters":5000}""", api.take().body!!.utf8())
    }

    @Test
    fun `매칭 조회는 대기 여부와 기본 대결 거리를 읽고 요청·취소도 기본 거리를 준다(FR-109)`() = runTest {
        api.reply("""{"waiting":false,"distanceMeters":null,"defaultDistanceMeters":3000}""")
        api.reply("""{"waiting":false,"distanceMeters":null,"defaultDistanceMeters":10000}""")

        assertEquals(Outcome.Success(MatchAcceptance(false, null, Distance(3_000))), repository.matchStatus())
        assertEquals("GET /api/v1/battles/match", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(Distance(10_000), repository.cancel().value().defaultDistance)
    }

    @Test
    fun `대결 결과는 상대 표시명·본인 기록 ID·도달 시간·이번 주 전적을 읽는다`() = runTest {
        api.reply(
            """{"battleId":9,"type":"RANDOM","pending":false,"retryUpload":false,"verdict":"WIN","corrected":false,
               |"myReachSeconds":1650,"opponentReachSeconds":1692,"rows":[{"item":"기록","mine":"27:30","theirs":"28:12"}],
               |"weekly":{"wins":3,"losses":1,"draws":0},"rewardPoints":40,"newRecord":false,"opponentName":"바람처럼",
               |"recordId":"5b2c-uuid"}
            """.trimMargin()
        )
        api.reply("""{"battleId":10,"pending":true,"recordId":null,"opponentName":null}""")

        val won = repository.result(9).value()
        val pending = repository.result(10).value()

        assertEquals("바람처럼", won.opponentName)
        assertEquals("5b2c-uuid", won.recordId)
        assertEquals(Duration.ofSeconds(1_650), won.myReach)
        assertEquals(Duration.ofSeconds(1_692), won.opponentReach)
        assertEquals(BattleResult.Weekly(3, 1, 0), won.weekly)
        assertEquals(null, pending.recordId)
        assertEquals(null, pending.weekly)
    }

    @Test
    fun `이력은 쪽 크기·커서를 보내고 기록 차이·출처·다음 커서를 읽는다`() = runTest {
        api.reply(
            """{"entries":[{"battleId":3,"type":"CELEBRITY","opponentName":"익명 마라토너","distanceMeters":10000,
               |"verdict":"LOSE","playedAt":"2026-10-18T07:30:00","newRecord":false,"gapSeconds":95,
               |"sourceLabel":"공개 기록 기준 · 2025 서울마라톤"}],"total":30,"wins":10,"losses":20,"selfNewRecords":2,
               |"nextCursor":3}
            """.trimMargin()
        )

        val history = repository.history(null, cursor = 23).value()

        val entry = history.entries.single()
        assertEquals(Duration.ofSeconds(95), entry.gap)
        assertEquals("공개 기록 기준 · 2025 서울마라톤", entry.sourceLabel)
        assertEquals(3L, history.nextCursor)
        val target = api.take().url
        assertEquals("23", target.queryParameter("cursor"))
        assertEquals("20", target.queryParameter("size"))
    }

    @Test
    fun `기록 대결 시작 정보의 상대 표를 OpponentTrack으로 바꾼다`() = runTest {
        api.reply(
            """{"battleId":9,"opponentName":"10/03의 나","distanceMeters":3000,
               |"track":[{"elapsedSeconds":0,"distanceMeters":0},{"elapsedSeconds":100,"distanceMeters":300},
               |{"elapsedSeconds":1000,"distanceMeters":3000}],"cardKind":"SELF_RUN"}
            """.trimMargin()
        )

        val start = repository.startSelfBattle(Distance(3_000)).value()

        assertEquals(9, start.battleId)
        assertEquals(CardKind.SELF_RUN, start.cardKind)
        assertEquals(Distance(150), start.track.distanceAt(Duration.ofSeconds(50)), "표본 사이 직선 보간")
        assertEquals(Duration.ofSeconds(1_000), start.track.arrivalTime(Distance(3_000)))
    }

    @Test
    fun `후보는 기록이 없으면 완주 시간이 없다`() = runTest {
        api.reply(
            """{"candidates":[{"displayName":"나","distanceMeters":5000},
               |{"id":3,"displayName":"익명 마라토너","distanceMeters":10000,"finishSeconds":2700,
               |"recordedOn":"2025-04-06","sourceLabel":"대회 기록","estimated":true}]}
            """.trimMargin()
        )

        val candidates = repository.celebrityCandidates(Distance(10_000)).value()

        assertEquals(
            RecordBattleCandidates(
                listOf(
                    RecordBattleCandidates.Candidate(null, "나", Distance(5_000), null, null, null, false),
                    RecordBattleCandidates.Candidate(
                        3,
                        "익명 마라토너",
                        Distance(10_000),
                        Duration.ofSeconds(2_700),
                        LocalDate.of(2025, 4, 6),
                        "대회 기록",
                        true
                    )
                )
            ),
            candidates
        )
        assertEquals("/api/v1/battles/celebrity/candidates?distanceMeters=10000", api.take().target)
    }

    @Test
    fun `이력의 모르는 대결 종류는 건너뛴다`() = runTest {
        api.reply(
            """{"entries":[{"battleId":1,"type":"GHOST","opponentName":"고스트","distanceMeters":3000,"verdict":"WIN",
               |"playedAt":"2026-10-18T07:30:00"},{"battleId":2,"type":"TEAM","opponentName":"?","distanceMeters":1000,
               |"playedAt":"2026-10-18T08:00:00"}],"total":2,"wins":1,"losses":0,"selfNewRecords":0}
            """.trimMargin()
        )

        val history = repository.history(BattleHistory.Type.RANDOM).value()

        assertEquals(
            listOf(
                BattleHistory.Entry(
                    1,
                    BattleHistory.Type.GHOST,
                    "고스트",
                    Distance(3_000),
                    "WIN",
                    Instant.parse("2026-10-17T22:30:00Z")
                )
            ),
            history.entries
        )
        assertEquals("/api/v1/battles/history?type=RANDOM&size=20", api.take().target)
    }

    @Test
    fun `그만두기 거부는 사유 코드로 온다`() = runTest {
        api.reply("""{"code":"INVALID_STATE"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("INVALID_STATE")), repository.forfeit(9))
        assertEquals("POST /api/v1/battles/9/forfeit", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }
}
