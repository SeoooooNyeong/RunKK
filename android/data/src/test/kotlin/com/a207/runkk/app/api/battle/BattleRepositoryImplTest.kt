package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
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
            listOf(BattleHistory.Entry(1, BattleHistory.Type.GHOST, "고스트", Distance(3_000), "WIN", Instant.parse("2026-10-17T22:30:00Z"))),
            history.entries
        )
        assertEquals("/api/v1/battles/history?type=RANDOM", api.take().target)
    }

    @Test
    fun `그만두기 거부는 사유 코드로 온다`() = runTest {
        api.reply("""{"code":"INVALID_STATE"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("INVALID_STATE")), repository.forfeit(9))
        assertEquals("POST /api/v1/battles/9/forfeit", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }
}
