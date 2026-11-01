package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleKind
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
    fun `상대 기록 미리 받기는 kind를 질의로 보내고 구간 기록을 OpponentTrack으로 바꾼다(D-377)`() = runTest {
        api.reply(
            """{"tracks":[{"celebrityId":null,"opponentName":"10/02의 나","distanceMeters":1000,"finishSeconds":300,
               |"track":[{"elapsedSeconds":150,"distanceMeters":500},{"elapsedSeconds":300,"distanceMeters":1000}],
               |"sourceLabel":null,"estimated":false,"celebrityFigure":null}]}
            """.trimMargin()
        )

        val tracks = repository.recordTracks(RecordBattleKind.SELF).value()

        val sent = api.take()
        assertEquals(
            "GET /api/v1/battles/record-tracks kind=SELF",
            "${sent.method} ${sent.url.encodedPath} ${sent.url.encodedQuery}"
        )
        val self = tracks.single()
        assertEquals(RecordBattleKind.SELF, self.kind)
        assertEquals(Distance(1_000), self.distance)
        assertEquals(null, self.celebrityId)
        assertEquals("10/02의 나", self.displayName)
        assertEquals(Duration.ofSeconds(300), self.finishTime)
        assertEquals(null, self.sourceLabel)
        assertEquals(null, self.celebrityFigure)
        assertEquals(Distance(500), self.track.distanceAt(Duration.ofSeconds(150)))
        assertEquals(Duration.ofSeconds(300), self.track.arrivalTime(Distance(1_000)))
    }

    @Test
    fun `셀럽 상대 기록은 셀럽 ID·출처·그림 키를 읽고 서버 순서를 지킨다(D-377)`() = runTest {
        api.reply(
            """{"tracks":[
               |{"celebrityId":3,"opponentName":"이**","distanceMeters":5000,"finishSeconds":1500,"track":[],
               | "sourceLabel":"2024 춘천마라톤","estimated":true,"celebrityFigure":"lee"},
               |{"celebrityId":4,"opponentName":"황**","distanceMeters":5000,"finishSeconds":1600,
               | "track":[{"elapsedSeconds":1600,"distanceMeters":5000}],"sourceLabel":null,"estimated":false,"celebrityFigure":" "}]}
            """.trimMargin()
        )

        val tracks = repository.recordTracks(RecordBattleKind.CELEBRITY).value()

        assertEquals("kind=CELEBRITY", api.take().url.encodedQuery)
        assertEquals(listOf(3L, 4L), tracks.map { it.celebrityId })
        assertEquals(RecordBattleKind.CELEBRITY, tracks.first().kind)
        assertEquals("2024 춘천마라톤", tracks.first().sourceLabel)
        assertEquals("lee", tracks.first().celebrityFigure)
        assertEquals(null, tracks.last().celebrityFigure, "빈 그림 키는 없음으로 본다")
        assertEquals(Distance(2_500), tracks.last().track.distanceAt(Duration.ofSeconds(800)))
        assertEquals(Distance.ZERO, tracks.first().track.distanceAt(Duration.ofSeconds(800)), "빈 구간 기록은 0m에 머문다")
        assertEquals(null, tracks.first().track.arrivalTime(Distance(5_000)), "빈 구간 기록은 도착하지 않는다")
    }

    @Test
    fun `셀럽 기록 하나는 경로에 셀럽 ID를 넣어 받고 구간 기록을 OpponentTrack으로 바꾼다(W31)`() = runTest {
        api.reply(
            """{"celebrityId":3,"opponentName":"이**","distanceMeters":5000,"finishSeconds":1500,
               |"track":[{"elapsedSeconds":750,"distanceMeters":2500},{"elapsedSeconds":1500,"distanceMeters":5000}],
               |"sourceLabel":"2024 춘천마라톤","estimated":true,"celebrityFigure":"lee"}
            """.trimMargin()
        )

        val celebrity = repository.celebrityTrack(3).value()

        val sent = api.take()
        assertEquals("GET /api/v1/battles/record-tracks/celebrity/3", "${sent.method} ${sent.url.encodedPath}")
        assertEquals(RecordBattleKind.CELEBRITY, celebrity.kind)
        assertEquals(3L, celebrity.celebrityId)
        assertEquals("이**", celebrity.displayName)
        assertEquals("2024 춘천마라톤", celebrity.sourceLabel)
        assertEquals("lee", celebrity.celebrityFigure)
        assertEquals(Duration.ofSeconds(1_500), celebrity.track.arrivalTime(Distance(5_000)))
    }

    @Test
    fun `셀럽 기록 하나가 없으면(404) 본문 code의 Rejected이고 code가 없으면 NOT_FOUND다(W31)`() = runTest {
        api.reply("""{"code":"CELEBRITY_NOT_FOUND","message":"없음"}""", code = 404)
        api.reply("", code = 404)

        assertEquals(Outcome.Failure(AppError.Rejected("CELEBRITY_NOT_FOUND")), repository.celebrityTrack(9))
        assertEquals(Outcome.Failure(AppError.Rejected("NOT_FOUND")), repository.celebrityTrack(9))
    }

    @Test
    fun `상대 기록이 없으면 빈 목록이고 잘못된 kind 거부(400)는 Rejected다`() = runTest {
        api.reply("""{"tracks":[]}""")
        api.reply("""{"code":"BAD_REQUEST"}""", code = 400)

        assertEquals(Outcome.Success(emptyList()), repository.recordTracks(RecordBattleKind.SELF))
        assertEquals(Outcome.Failure(AppError.Rejected("BAD_REQUEST")), repository.recordTracks(RecordBattleKind.SELF))
    }

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
    fun `대결 결과는 상대 표시명·본인 기록 ID·도달 시간·종류·적립 합계·평가를 읽는다(D-283, D-284)`() = runTest {
        api.reply(
            """{"battleId":9,"type":"FRIEND","pending":false,"retryUpload":false,"verdict":"WIN","corrected":false,
               |"myReachSeconds":1650,"opponentReachSeconds":1692,"rows":[],
               |"weekly":{"wins":3,"losses":1,"draws":0},"rewardPoints":0,"newRecord":false,"opponentName":"바람처럼",
               |"recordId":"5b2c-uuid","totalRewardPoints":12,"opponentGender":"FEMALE",
               |"evaluation":{"kind":"FIRST_PLACEMENT","tier":"SILVER","validRuns":3,"runimalType":null}}
            """.trimMargin()
        )
        api.reply("""{"battleId":10,"pending":true,"recordId":null,"opponentName":null}""")

        val won = repository.result(9).value()
        val pending = repository.result(10).value()

        assertEquals("바람처럼", won.opponentName)
        assertEquals("5b2c-uuid", won.recordId)
        assertEquals(Duration.ofSeconds(1_650), won.myReach)
        assertEquals(Duration.ofSeconds(1_692), won.opponentReach)
        assertEquals(BattleHistory.Type.FRIEND, won.type)
        assertEquals(12, won.totalRewardPoints)
        assertEquals(BattleResult.Evaluation("FIRST_PLACEMENT", "SILVER", 3, null), won.evaluation)
        assertEquals(null, pending.recordId)
        assertEquals(null, pending.totalRewardPoints, "옛 서버면 합계 없음")
        assertEquals(null, pending.evaluation)
        assertEquals(Gender.FEMALE, won.opponentGender)
        assertEquals(null, pending.opponentGender, "옛 서버면 상대 성별 없음")
    }

    @Test
    fun `친구 초대 만들기는 거리를 보내고 링크·만료 시각을 읽는다(D-270)`() = runTest {
        api.reply(
            """{"inviteId":41,"token":"aB3-_xY0aB3-_xY0aB3-_x",
               |"inviteUrl":"https://k15a207.p.ssafy.io/invite/battle/aB3-_xY0aB3-_xY0aB3-_x",
               |"distanceMeters":5000,"expiresAt":"2026-10-30T10:05:00"}
            """.trimMargin(),
            code = 201
        )

        val invite = repository.createFriendInvite(Distance(5_000)).value()

        assertEquals(
            FriendInvite(
                41,
                "aB3-_xY0aB3-_xY0aB3-_x",
                "https://k15a207.p.ssafy.io/invite/battle/aB3-_xY0aB3-_xY0aB3-_x",
                Distance(5_000),
                Instant.parse("2026-10-30T01:05:00Z")
            ),
            invite
        )
        val request = api.take()
        assertEquals("POST /api/v1/battles/friend-invites", "${request.method} ${request.url.encodedPath}")
        assertEquals("""{"distanceMeters":5000}""", request.body!!.utf8())
    }

    @Test
    fun `내 열린 초대는 200이면 초대, 204면 null이다(D-271)`() = runTest {
        api.reply(
            """{"inviteId":41,"token":"t1","inviteUrl":"https://k15a207.p.ssafy.io/invite/battle/t1",
               |"distanceMeters":3000,"expiresAt":"2026-10-30T10:05:00"}
            """.trimMargin()
        )
        api.replyEmpty(204)

        assertEquals(41L, repository.myFriendInvite().value()?.inviteId)
        assertEquals("GET /api/v1/battles/friend-invites/mine", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(Outcome.Success(null), repository.myFriendInvite())
    }

    @Test
    fun `초대 취소는 DELETE이고 거부는 본문 code로 온다(TC-9669, TC-9686)`() = runTest {
        api.replyEmpty(204)
        api.reply("""{"code":"INVITE_ALREADY_JOINED","message":"이미 참가했어요"}""", code = 409)
        api.reply("""{"code":"PERMISSION_DENIED"}""", code = 403)

        assertEquals(Outcome.Success(Unit), repository.cancelFriendInvite(41))
        assertEquals(
            "DELETE /api/v1/battles/friend-invites/41",
            api.take().let { "${it.method} ${it.url.encodedPath}" }
        )
        assertEquals(Outcome.Failure(AppError.Rejected("INVITE_ALREADY_JOINED")), repository.cancelFriendInvite(41))
        assertEquals(Outcome.Failure(AppError.Rejected("PERMISSION_DENIED")), repository.cancelFriendInvite(41))
    }

    @Test
    fun `받은 초대는 me를 펴서 읽고 없는 토큰은 INVITE_NOT_FOUND다(D-273)`() = runTest {
        api.reply(
            """{"inviteId":41,"status":"PENDING","distanceMeters":5000,"expiresAt":"2026-10-30T10:05:00",
               |"inviter":{"name":"바람처럼","tier":"GOLD","bestSeconds":1788},"me":{"tier":"UNRANKED","bestSeconds":null},
               |"blockedReason":null}
            """.trimMargin()
        )
        api.reply("""{"code":"INVITE_NOT_FOUND"}""", code = 409)

        assertEquals(
            FriendInviteView(
                inviteId = 41,
                status = "PENDING",
                distance = Distance(5_000),
                expiresAt = Instant.parse("2026-10-30T01:05:00Z"),
                inviter = FriendInviteView.Inviter("바람처럼", "GOLD", 1_788),
                myTier = "UNRANKED",
                myBestSeconds = null,
                blockedReason = null
            ),
            repository.friendInvite("aB3-_x").value()
        )
        assertEquals(
            "GET /api/v1/battles/friend-invites/by-token/aB3-_x",
            api.take().let { "${it.method} ${it.url.encodedPath}" }
        )
        assertEquals(Outcome.Failure(AppError.Rejected("INVITE_NOT_FOUND")), repository.friendInvite("zz"))
    }

    @Test
    fun `참가는 대결 ID를 읽고 거절은 204다(D-273, D-274)`() = runTest {
        api.reply("""{"battleId":77}""")
        api.replyEmpty(204)

        assertEquals(Outcome.Success(77L), repository.acceptFriendInvite("tok"))
        assertEquals(
            "POST /api/v1/battles/friend-invites/by-token/tok/accept",
            api.take().let { "${it.method} ${it.url.encodedPath}" }
        )
        assertEquals(Outcome.Success(Unit), repository.declineFriendInvite("tok"))
        assertEquals(
            "POST /api/v1/battles/friend-invites/by-token/tok/decline",
            api.take().let { "${it.method} ${it.url.encodedPath}" }
        )
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
               |"recordedOn":"2025-04-06","sourceLabel":"대회 기록","estimated":true,"myBestSeconds":3730}]}
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
                        true,
                        myBest = Duration.ofSeconds(3_730)
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
    fun `진행 중인 대결은 상대 이름과 출발 시각을 읽고 없으면(204) null이다(배틀 QA 7번)`() = runTest {
        api.reply(
            """{"battleId":12,"type":"RANDOM","status":"WAITING_START","distanceMeters":5000,"opponentName":"러너2",
               |"startAt":"2026-10-27T16:00:13","opponentGender":"MALE"}
            """.trimMargin()
        )
        api.reply(
            """{"battleId":13,"type":"TEAM","status":"IN_PROGRESS","distanceMeters":5000,"opponentName":null,
               |"startAt":null}
            """.trimMargin()
        )
        api.replyEmpty(204)

        assertEquals(
            Outcome.Success(
                ActiveBattle(
                    12,
                    BattleHistory.Type.RANDOM,
                    "WAITING_START",
                    Distance(5_000),
                    "러너2",
                    Instant.parse("2026-10-27T07:00:13Z"),
                    Gender.MALE
                )
            ),
            repository.active()
        )
        assertEquals("GET /api/v1/battles/active", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(
            Outcome.Success(ActiveBattle(13, null, "IN_PROGRESS", Distance(5_000), null, null)),
            repository.active(),
            "모르는 종류여도 그만두기는 할 수 있게 읽는다"
        )
        assertEquals(Outcome.Success(null), repository.active())
    }

    @Test
    fun `그만두기 거부는 사유 코드로 온다`() = runTest {
        api.reply("""{"code":"INVALID_STATE"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("INVALID_STATE")), repository.forfeit(9))
        assertEquals("POST /api/v1/battles/9/forfeit", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }
}
