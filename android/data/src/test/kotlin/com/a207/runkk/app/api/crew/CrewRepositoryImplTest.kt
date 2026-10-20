package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.VoteAnswer
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.crew 매퍼(14장 단위(앱 데이터)): 크루 목록·게시판·대기실·크루런 결과 DTO → 도메인, 갈래별 엔드포인트(D-36). */
class CrewRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create())

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
            CrewList(listOf(CrewList.Item(1, "새벽런", null, setOf("MORNING"), "곰", 12, Distance(81_000), JoinPolicy.FREE))),
            crews
        )
        val target = api.take().url
        assertEquals(listOf("MORNING", "WEEKEND"), target.queryParameterValues("tags"))
        assertEquals("새벽", target.queryParameter("keyword"))
    }

    @Test
    fun `크루런 모집글은 거리·모임 시각을 실어 게시판으로 보낸다`() = runTest {
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
            """{"kind":"CREW_RUN","title":"토요 한강런","distanceMeters":10000,"meetAt":"2026-10-25T06:00:00"}""",
            request.body!!.utf8()
        )
    }

    @Test
    fun `가입 신청은 크루 ID 또는 초대 코드로 같은 엔드포인트에 간다`() = runTest {
        api.replyEmpty()
        api.replyEmpty()

        repository.apply(5)
        repository.applyWithInviteCode("AB12CD")

        assertEquals("""{"crewId":5}""", api.take().body!!.utf8())
        assertEquals("""{"inviteCode":"AB12CD"}""", api.take().body!!.utf8())
    }

    @Test
    fun `게시판의 모르는 글 종류는 건너뛴다`() = runTest {
        api.reply(
            """{"posts":[{"postId":1,"kind":"CHEER","authorName":"곰","summary":"화이팅","writtenAt":"2026-10-20T07:00:00","mine":true},
               |{"postId":2,"kind":"POLL","authorName":"여우","summary":"?","writtenAt":"2026-10-20T08:00:00"}]}
            """.trimMargin()
        )

        val posts = repository.posts(5, null).value()

        assertEquals(
            PostList(listOf(PostList.Item(1, PostList.Kind.CHEER, "곰", "화이팅", Instant.parse("2026-10-19T22:00:00Z"), true))),
            posts
        )
    }

    @Test
    fun `대기실·투표·결과를 바꾼다`() = runTest {
        api.reply("""{"postId":77,"participants":[{"displayName":"곰","ready":true}],"meetAt":"2026-10-25T06:00:00"}""")
        api.replyEmpty()
        api.reply(
            """{"postId":77,"finishers":[{"rank":1,"displayName":"곰","elapsedSeconds":3000,"averagePaceSecondsPerKm":300},
               |{"displayName":"토끼"}],"firstCrewRun":true,"raceContributionMeters":20000}
            """.trimMargin()
        )

        val lobby = repository.toggleReady(77).value()
        repository.vote(77, VoteAnswer.GOING)
        val result = repository.crewRunResult(77).value()

        assertEquals(Lobby(77, listOf(Lobby.Participant("곰", true)), Instant.parse("2026-10-24T21:00:00Z")), lobby)
        assertEquals("/api/v1/crew-runs/77/lobby/ready", api.take().url.encodedPath)
        assertEquals("""{"answer":"GOING"}""", api.take().body!!.utf8())
        assertEquals(
            CrewRunResult(
                77,
                listOf(CrewRunResult.Finisher(1, "곰", 3_000, 300), CrewRunResult.Finisher(null, "토끼", null, null)),
                true,
                Distance(20_000)
            ),
            result
        )
    }
}
