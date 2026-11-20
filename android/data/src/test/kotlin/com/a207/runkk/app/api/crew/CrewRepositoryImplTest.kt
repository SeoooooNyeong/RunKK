package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunEdit
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.MyRunState
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.crew 매퍼(14장 단위(앱 데이터)): 크루 목록·게시판·대기실·크루런 결과 DTO → 도메인, 갈래별 엔드포인트(D-36). */
class CrewRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), api.faces)

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `크루 검색 결과를 바꾸고 태그를 반복 매개변수로 보낸다`() = runTest {
        api.reply(
            """{"crews":[{"crewId":1,"name":"새벽런","tags":["MORNING"],"leaderName":"곰","headcount":12,
               |"weeklyDistanceMeters":81000,"joinPolicy":"FREE"}]}
            """.trimMargin()
        )

        val crews = repository.search("새벽", setOf("WEEKEND", "MORNING")).value()

        assertEquals(
            CrewList(
                listOf(CrewList.Item(1, "새벽런", null, setOf("MORNING"), "곰", 12, Distance(81_000), JoinPolicy.FREE))
            ),
            crews
        )
        val target = api.take().url
        assertEquals(listOf("MORNING", "WEEKEND"), target.queryParameterValues("tags"))
        assertEquals("새벽", target.queryParameter("keyword"))
    }

    @Test
    fun `크루런 모집글은 제목·내용·거리·모임 시각·반복을 따로 실어 게시판으로 보낸다(W6)`() = runTest {
        api.reply("""{"id":77}""")

        val id =
            repository.writeCrewRun(
                5,
                CrewRunForm("토요 한강런", null, Distance(10_000), Instant.parse("2026-10-24T21:00:00Z"))
            )

        assertEquals(Outcome.Success(77L), id)
        val request = api.take()
        assertEquals("/api/v1/crews/5/posts", request.url.encodedPath)
        assertEquals(
            """{"kind":"CREW_RUN","body":"","distanceMeters":10000,"scheduledAt":"2026-10-25T06:00:00+09:00",""" +
                """"title":"토요 한강런","repeat":"NONE"}""",
            request.body!!.utf8()
        )
    }

    @Test
    fun `제한 없음 반복 모집글은 거리를 싣지 않고 WEEKLY를 보낸다(W6)`() = runTest {
        api.reply("""{"id":78}""")
        repository.writeCrewRun(
            5,
            CrewRunForm("멀리", "8번 출구", null, Instant.parse("2026-10-24T21:00:00Z"), CrewRunRepeat.WEEKLY)
        )
        assertEquals(
            """{"kind":"CREW_RUN","body":"8번 출구","scheduledAt":"2026-10-25T06:00:00+09:00",""" +
                """"title":"멀리","repeat":"WEEKLY"}""",
            api.take().body!!.utf8()
        )
    }

    @Test
    fun `일정 상세·수정·참석 취소는 crew-runs와 attendance 경로를 쓰고 상세를 읽는다(W6)`() = runTest {
        val detailJson =
            """{"postId":42,"crewId":7,"title":"토요일 한강","body":"","authorId":3,"authorName":"곰","mine":true,
               |"writtenAt":"2026-10-21T10:12:00+09:00","scheduledAt":"2026-10-24T07:00:00+09:00",
               |"voteDeadline":"2026-10-24T07:00:00+09:00","status":"VOTING","goingCount":1,"myAnswer":"GOING",
               |"canEdit":true,"canDelete":true,"repeat":"WEEKLY","repeatDayOfWeek":"SATURDAY",
               |"attendees":[{"memberId":3,"displayName":"곰","author":true,"me":true,"faceKey":null,"shownGender":"MALE",
               |"distanceMeters":3200},{"memberId":4,"displayName":"범","author":false,"me":false}]}
            """.trimMargin()
        api.reply(detailJson)
        val detail = repository.crewRun(42).value()
        assertEquals("GET /api/v1/crew-runs/42", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(null, detail.distance)
        assertEquals(CrewRunRepeat.WEEKLY, detail.repeat)
        assertEquals(java.time.DayOfWeek.SATURDAY, detail.repeatDay)
        assertEquals(true, detail.isGoing)
        assertEquals(
            CrewRunDetail.Attendee(
                3,
                "곰",
                isAuthor = true,
                isMe = true,
                shownGender = Gender.MALE,
                distance = Distance(3_200)
            ),
            detail.attendees[0]
        )
        assertEquals(null, detail.attendees[1].distance, "달린 거리가 없으면(마치기 전·안 달림) null(DV-2)")

        api.reply(detailJson)
        repository.editCrewRun(42, CrewRunEdit("하프", null, Distance(21_097), CrewRunRepeat.NONE))
        val edit = api.take()
        assertEquals("PATCH /api/v1/crew-runs/42", "${edit.method} ${edit.url.encodedPath}")
        assertEquals("""{"title":"하프","body":"","distanceMeters":21097,"repeat":"NONE"}""", edit.body!!.utf8())

        api.reply("""{"postId":42,"myAnswer":null,"goingCount":0,"notGoingCount":0,"undecidedCount":0}""")
        assertEquals(Outcome.Success(Unit), repository.withdrawAttendance(42))
        assertEquals("DELETE /api/v1/posts/42/attendance", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `가입 신청은 크루 ID 또는 초대 코드로 같은 엔드포인트에 가고 결과(상태·크루·신청 ID)를 돌려준다`() = runTest {
        api.reply("""{"status":"PENDING","crewId":5,"requestId":31}""")
        api.reply("""{"status":"JOINED","crewId":8,"requestId":null}""")

        val applied = repository.apply(5).value()
        val invited = repository.applyWithInviteCode("AB12CD").value()

        assertEquals(JoinResult(JoinResult.Status.PENDING, 5, 31), applied)
        assertEquals(JoinResult(JoinResult.Status.JOINED, 8, null), invited)
        assertEquals("""{"crewId":5}""", api.take().body!!.utf8())
        assertEquals("""{"inviteCode":"AB12CD"}""", api.take().body!!.utf8())
    }

    @Test
    fun `내 크루는 소속 크루 상세와 대기 중인 본인 가입 신청을 읽는다`() = runTest {
        api.reply(
            """{"crew":{"crewId":9,"name":"새벽런","intro":"같이 달려요","tags":["DAWN"],"visibility":"PRIVATE",
               |"joinPolicy":"APPROVAL","logoUrl":null,"leaderId":3,"leaderName":"곰","headcount":4,"weeklyDistanceMeters":12000,
               |"myRole":"LEADER","inviteCode":"K7P2QX","inviteCodeExpiresAt":"2026-10-27T09:00:00"},"pendingRequests":[]}
            """.trimMargin()
        )
        api.reply(
            """{"crew":null,"pendingRequests":[{"requestId":31,"crewId":5,"crewName":"저녁런","requestedAt":"2026-10-20T07:00:00"}]}"""
        )

        val leader = repository.myCrew().value()
        val waiting = repository.myCrew().value()

        assertEquals(
            CrewDetail(
                crewId = 9,
                name = "새벽런",
                intro = "같이 달려요",
                tags = setOf("DAWN"),
                visibility = CrewVisibility.PRIVATE,
                joinPolicy = JoinPolicy.APPROVAL,
                logoUrl = null,
                leaderId = 3,
                leaderName = "곰",
                headcount = 4,
                weeklyDistance = Distance(12_000),
                myRole = CrewRole.LEADER,
                inviteCode = "K7P2QX",
                inviteCodeExpiresAt = Instant.parse("2026-10-27T00:00:00Z")
            ),
            leader.crew
        )
        assertEquals(true, leader.crew!!.isLeader)
        assertEquals("/api/v1/crews/me", api.take().url.encodedPath)
        assertEquals(
            MyCrew(null, listOf(MyCrew.PendingJoin(31, 5, "저녁런", Instant.parse("2026-10-19T22:00:00Z")))),
            waiting
        )
    }

    @Test
    fun `크루 한 곳은 크루원 역할로 읽고 크루 밖이면 역할·초대 코드가 없다`() = runTest {
        api.reply(
            """{"crewId":9,"name":"새벽런","intro":null,"tags":[],"visibility":"PUBLIC","joinPolicy":"FREE","leaderId":3,
               |"leaderName":null,"headcount":4,"weeklyDistanceMeters":0,"myRole":null}
            """.trimMargin()
        )

        val detail = repository.detail(9).value()

        assertEquals("/api/v1/crews/9", api.take().url.encodedPath)
        assertEquals(null, detail.myRole)
        assertEquals(false, detail.isLeader)
        assertEquals(null, detail.inviteCode)
        assertEquals("", detail.leaderName)
    }

    @Test
    fun `목록은 쪽 크기와 커서를 보내고 다음 커서를 돌려준다`() = runTest {
        api.reply("""{"crews":[],"nextCursor":40}""")
        api.reply("""{"posts":[],"nextCursor":null}""")

        val crews = repository.search(null, emptySet(), cursor = 60).value()
        val posts = repository.posts(5, PostList.Kind.CHEER).value()

        assertEquals(40L, crews.nextCursor)
        assertEquals(null, posts.nextCursor)
        val crewUrl = api.take().url
        assertEquals("60", crewUrl.queryParameter("cursor"))
        assertEquals("20", crewUrl.queryParameter("size"))
        val postUrl = api.take().url
        assertEquals(null, postUrl.queryParameter("cursor"))
        assertEquals("20", postUrl.queryParameter("size"))
        assertEquals("CHEER", postUrl.queryParameter("kind"))
    }

    @Test
    fun `크루원·레이스 기여자는 본인 표시(me)와 회원 ID를 읽는다`() = runTest {
        api.reply(
            """{"members":[{"memberId":1,"displayName":"곰","role":"LEADER","me":false},
               |{"memberId":7,"displayName":"나","role":"MEMBER","me":true}]}
            """.trimMargin()
        )
        api.reply(
            """{"weekStart":"2026-10-19","totalMeters":20000,"closesAt":"2026-10-26T00:00:00","myShare":40,
               |"contributors":[{"displayName":"나","distanceMeters":8000,"share":40,"memberId":7,"me":true},
               |{"displayName":null,"distanceMeters":12000,"share":60,"memberId":null,"me":false}]}
            """.trimMargin()
        )

        val members = repository.members(9).value()
        val race = repository.race(9, null).value()

        assertEquals(listOf(false, true), members.members.map { it.isMe })
        assertEquals(listOf(CrewRole.LEADER, CrewRole.MEMBER), members.members.map { it.role })
        assertEquals(
            listOf(
                RaceStatus.Contributor("나", Distance(8_000), 40.0, 7, isMe = true),
                RaceStatus.Contributor("", Distance(12_000), 60.0, null, isMe = false)
            ),
            race.contributors
        )
    }

    @Test
    fun `게시판의 모르는 글 종류는 건너뛴다`() = runTest {
        api.reply(
            """{"posts":[{"postId":1,"kind":"CHEER","authorName":"곰","body":"화이팅","writtenAt":"2026-10-20T07:00:00","mine":true},
               |{"postId":2,"kind":"FUTURE_KIND","authorName":"여우","body":"?","writtenAt":"2026-10-20T08:00:00"}]}
            """.trimMargin()
        )

        val posts = repository.posts(5, null).value()

        assertEquals(
            PostList(
                listOf(PostList.Item(1, PostList.Kind.CHEER, "곰", "화이팅", Instant.parse("2026-10-19T22:00:00Z"), true))
            ),
            posts
        )
    }

    @Test
    fun `크루런 모집글은 서버 crewRun(거리·일시·상태·인원·내 응답)을 싣고 본문이 비면 빈 글로 읽는다`() = runTest {
        api.reply(
            """{"posts":[{"postId":3,"kind":"CREW_RUN","authorName":"곰","body":null,"writtenAt":"2026-10-20T07:00:00",
               |"mine":false,"crewRun":{"distanceMeters":5000,"scheduledAt":"2026-10-25T06:00:00",
               |"voteDeadline":"2026-10-25T05:00:00","status":"VOTING","goingCount":2,"myAnswer":"GOING","finisherCount":0,
               |"raceContributionMeters":null}}]}
            """.trimMargin()
        )

        val posts = repository.posts(5, PostList.Kind.CREW_RUN).value()

        assertEquals(
            PostList(
                listOf(
                    PostList.Item(
                        3,
                        PostList.Kind.CREW_RUN,
                        "곰",
                        "",
                        Instant.parse("2026-10-19T22:00:00Z"),
                        false,
                        PostList.CrewRunInfo(
                            distance = Distance(5_000),
                            scheduledAt = Instant.parse("2026-10-24T21:00:00Z"),
                            voteDeadline = Instant.parse("2026-10-24T20:00:00Z"),
                            status = CrewRunStatus.VOTING,
                            goingCount = 2,
                            myAnswer = AttendanceAnswer.GOING,
                            finisherCount = 0,
                            raceContribution = null
                        )
                    )
                )
            ),
            posts
        )
    }

    @Test
    fun `대기실·투표·결과를 바꾼다(크루원 얼굴 키·표시 성별 포함, 옛 서버면 null)`() = runTest {
        api.reply(
            """{"postId":77,"scheduledAt":"2026-10-25T06:00:00","distanceMeters":3000,"remainingSeconds":600,
               |"status":"LOBBY_OPEN","participants":[{"memberId":3,"displayName":"곰","readiness":"READY","ready":true,"me":false,
               |"faceKey":"default:FOX","shownGender":"FEMALE",
               |"look":{"gender":"FEMALE","equipped":{"BOTTOM":5},"bodyVariant":2,"runAmount":10,"faceKey":"default:FOX"}},
               |{"memberId":7,"displayName":"나","readiness":"ENTERING","ready":false,"me":true}]}
            """.trimMargin()
        )
        api.reply("""{"postId":77,"myAnswer":"GOING","goingCount":3,"notGoingCount":1,"undecidedCount":0}""")
        api.reply(
            """{"postId":77,"status":"FINISHED","title":"오늘 저녁 한강 5km","distanceMeters":5000,
               |"startedAt":"2026-10-25T06:00:03+09:00","totalDistanceMeters":9100,
               |"participants":[{"memberId":3,"displayName":"곰","distanceMeters":5280,"elapsedSeconds":1712,
               |"averagePaceSecondsPerKm":324,"ended":true,"me":false,"faceKey":"default:FOX","shownGender":"MALE"},
               |{"memberId":7,"displayName":"토끼","distanceMeters":3820,"ended":true,"me":true}],
               |"myRecordId":"rec-7","firstCrewRun":true,"raceContributionMeters":20000}
            """.trimMargin()
        )

        val lobby = repository.toggleReady(77).value()
        repository.answerAttendance(77, AttendanceAnswer.GOING)
        val result = repository.crewRunResult(77).value()

        assertEquals(
            Lobby(
                77,
                listOf(
                    Lobby.Participant(
                        "곰",
                        true,
                        3,
                        isMe = false,
                        isEntered = true,
                        faceKey = "default:FOX",
                        shownGender = Gender.FEMALE,
                        look = RunnerLook(Gender.FEMALE, null, 5, null, 2, 10, "default:FOX")
                    ),
                    Lobby.Participant("나", false, 7, isMe = true, isEntered = true)
                ),
                Instant.parse("2026-10-24T21:00:00Z"),
                Distance(3_000)
            ),
            lobby
        )
        assertEquals("/api/v1/crew-runs/77/lobby/ready", api.take().url.encodedPath)
        val attendance = api.take()
        assertEquals("PUT /api/v1/posts/77/attendance", "${attendance.method} ${attendance.url.encodedPath}")
        assertEquals("""{"answer":"GOING"}""", attendance.body!!.utf8())
        assertEquals(
            CrewRunResult(
                postId = 77,
                participants = listOf(
                    CrewRunResult.Participant(
                        "곰",
                        Distance(5_280),
                        1_712,
                        324,
                        hasEnded = true,
                        memberId = 3,
                        faceKey = "default:FOX",
                        shownGender = Gender.MALE
                    ),
                    CrewRunResult.Participant("토끼", Distance(3_820), null, null, memberId = 7, isMe = true)
                ),
                totalDistance = Distance(9_100),
                isFirstCrewRun = true,
                raceContribution = Distance(20_000),
                status = CrewRunStatus.FINISHED,
                title = "오늘 저녁 한강 5km",
                distance = Distance(5_000),
                startedAt = Instant.parse("2026-10-24T21:00:03Z"),
                myRecordId = "rec-7"
            ),
            result
        )
    }

    @Test
    fun `내 크루런 끝내기는 마지막 거리·경과를, 일시정지는 정지 때 경과를 싣는다`() = runTest {
        api.replyEmpty()
        api.replyEmpty()
        api.replyEmpty()

        repository.quit(77, 4_870, Duration.ofSeconds(1_668))
        repository.pause(77, Duration.ofSeconds(754))
        repository.quit(78)

        val quit = api.take()
        assertEquals("POST /api/v1/crew-runs/77/quit", "${quit.method} ${quit.url.encodedPath}")
        assertEquals("""{"distanceMeters":4870,"elapsedSeconds":1668}""", quit.body!!.utf8())
        val pause = api.take()
        assertEquals("POST /api/v1/crew-runs/77/pause", "${pause.method} ${pause.url.encodedPath}")
        assertEquals("""{"elapsedSeconds":754}""", pause.body!!.utf8())
        assertEquals("""{}""", api.take().body!!.utf8())
    }

    @Test
    fun `크루런 목록·상세는 내 참여 상태 myRun을 읽고 없거나 모르는 값이면 NONE이다`() = runTest {
        fun post(myRun: String) =
            """{"postId":3,"kind":"CREW_RUN","authorName":"곰","body":"","writtenAt":"2026-10-20T07:00:00",
               |"crewRun":{"scheduledAt":"2026-10-25T06:00:00","status":"RUNNING"$myRun}}
            """.trimMargin()
        api.reply("""{"posts":[${post(""","myRun":"ENDED"""")},${post(""","myRun":"RUNNING"""")}]}""")
        api.reply("""{"posts":[${post("")},${post(""","myRun":"FUTURE"""")}]}""")

        val known = repository.posts(5, PostList.Kind.CREW_RUN).value().posts.map { it.crewRun?.myRun }
        val unknown = repository.posts(5, PostList.Kind.CREW_RUN).value().posts.map { it.crewRun?.myRun }

        assertEquals(listOf(MyRunState.ENDED, MyRunState.RUNNING), known)
        assertEquals(listOf(MyRunState.NONE, MyRunState.NONE), unknown, "옛 서버·모르는 값은 NONE")

        val detail =
            """{"postId":42,"crewId":7,"title":"토요일 한강","writtenAt":"2026-10-21T10:12:00+09:00",
               |"scheduledAt":"2026-10-24T07:00:00+09:00","status":"RUNNING""""
                .trimMargin()
        api.reply("""$detail,"myRun":"RUNNING"}""")
        api.reply("""$detail}""")
        assertEquals(MyRunState.RUNNING, repository.crewRun(42).value().myRun)
        assertEquals(MyRunState.NONE, repository.crewRun(42).value().myRun)
    }
}
