package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.BoardPost
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.Notice
import com.a207.runkk.app.domain.crew.Poll
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import retrofit2.create

/**
 * api.crew v0.11(F.2.2, F.2.4, D-98~D-100, D-105, D-114): 새 게시 종류 해독, 시각 두 형식, 운영진·공동 목표.
 * 서버 S3a 구현 전이라 05-03·S0 DTO 기준이다. 대면 모임·공지·투표·게시판·앨범 쓰기 연산은 앱에서 지웠다(S15P31A207-236).
 */
class CrewBoardAndScheduleTest {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), api.faces)

    @AfterTest
    fun tearDown() = api.close()

    private fun path(): String = api.take().let { "${it.method} ${it.url.encodedPath}" }

    @Test
    fun `TC-6856 게시 종류 6개를 해독하고 표에 없는 종류의 글만 버린다`() = runTest {
        api.reply(
            """{"posts":[
               |{"postId":1,"kind":"CHEER","authorName":"a","body":"화이팅","writtenAt":"2026-10-25T07:00:00+09:00","mine":true,"canDelete":true},
               |{"postId":2,"kind":"CREW_RUN","authorName":"a","body":"","writtenAt":"2026-10-25T07:00:00+09:00",
               | "crewRun":{"distanceMeters":5000,"scheduledAt":"2026-10-26T06:00:00+09:00","status":"RECRUITING","goingCount":3,
               | "myAnswer":"UNDECIDED","notGoingCount":1,"undecidedCount":2}},
               |{"postId":3,"kind":"MEETUP","authorName":"b","body":"모여요","writtenAt":"2026-10-25T07:00:00+09:00",
               | "meetup":{"scheduledAt":"2026-10-27T19:00:00+09:00","placeName":"여의나루","capacity":8,"goingCount":2,
               | "notGoingCount":0,"undecidedCount":1,"myAnswer":"GOING"}},
               |{"postId":4,"kind":"NOTICE","authorName":"c","body":"공지","writtenAt":"2026-10-25T07:00:00+09:00","notice":{"pinned":true}},
               |{"postId":5,"kind":"POLL","authorName":"c","body":"언제?","writtenAt":"2026-10-25T07:00:00+09:00",
               | "poll":{"method":"CHOICES","closesAt":"2026-10-28T00:00:00+09:00","closed":false,"responseCount":4,"myOptionNo":2}},
               |{"postId":6,"kind":"BOARD","authorName":"d","body":"사진","writtenAt":"2026-10-25T07:00:00+09:00",
               | "photos":[{"photoId":61,"url":"/api/v1/crew-photos/61"}]},
               |{"postId":7,"kind":"EVENT","authorName":"e","body":"?","writtenAt":"2026-10-25T07:00:00+09:00"}],"nextCursor":null}
            """.trimMargin()
        )

        val posts = repository.posts(5, null).value().posts

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), posts.map { it.postId }, "EVENT 글만 버린다")
        assertEquals(PostList.Kind.entries, posts.map { it.kind })
        assertTrue(posts[0].canDelete && posts[0].isMine)
        val crewRun = requireNotNull(posts[1].crewRun)
        assertEquals(AttendanceAnswer.UNDECIDED, crewRun.myAnswer)
        assertEquals(1 to 2, crewRun.notGoingCount to crewRun.undecidedCount)
        assertEquals(
            Meetup(Instant.parse("2026-10-27T10:00:00Z"), "여의나루", 8, 2, 0, 1, AttendanceAnswer.GOING),
            posts[2].meetup
        )
        assertEquals(Notice(isPinned = true), posts[3].notice)
        assertEquals(Poll(PollMethod.CHOICES, Instant.parse("2026-10-27T15:00:00Z"), false, 4, 2), posts[4].poll)
        assertEquals(BoardPost("사진", listOf(BoardPost.Photo(61, "/api/v1/crew-photos/61"))), posts[5].board)
    }

    @Test
    fun `TC-6858 크루 시각은 오프셋 있는 값과 없는 값을 같은 시각으로 읽는다(D-114)`() = runTest {
        for (writtenAt in listOf("2026-10-25T07:00:00", "2026-10-25T07:00:00+09:00")) {
            api.reply(
                """{"posts":[{"postId":1,"kind":"CHEER","authorName":"a","body":"b","writtenAt":"$writtenAt"}]}"""
            )

            val post = repository.posts(5, null).value().posts.single()

            assertEquals(Instant.parse("2026-10-24T22:00:00Z"), post.writtenAt, writtenAt)
        }
    }

    @Test
    fun `운영진 지정·해제는 크루장 경로로 보낸다(D-98)`() = runTest {
        api.reply("""{"memberId":9,"role":"MANAGER"}""")
        api.reply("""{"code":"MANAGER_LIMIT","message":"운영진은 2명까지"}""", code = 409)
        api.reply("""{"memberId":9,"role":"MEMBER"}""")

        assertEquals(Outcome.Success(Unit), repository.appointManager(5, 9))
        assertEquals(Outcome.Failure(AppError.Rejected("MANAGER_LIMIT")), repository.appointManager(5, 10))
        assertEquals(Outcome.Success(Unit), repository.dismissManager(5, 9))
        assertEquals("PUT /api/v1/crews/5/managers/9", path())
        path()
        assertEquals("DELETE /api/v1/crews/5/managers/9", path())
    }

    @Test
    fun `공동 목표`() = runTest {
        api.reply(
            """{"weekStart":"2026-10-19","totalMeters":52000,"closesAt":"2026-10-26T00:00:00+09:00","contributors":[],
               |"goalMeters":50000,"goalProgressPercent":100,"achievedAt":"2026-10-25T08:00:00+09:00",
               |"celebration":{"crewName":"새벽런","totalMeters":52000,"goalMeters":50000,"participantCount":6}}
            """.trimMargin()
        )

        val race = repository.setWeeklyGoal(5, Distance(50_000)).value()

        assertEquals(Distance(50_000), race.goal)
        assertEquals(100, race.goalProgressPercent)
        assertEquals(Instant.parse("2026-10-24T23:00:00Z"), race.achievedAt)
        assertEquals(CelebrationCard("새벽런", Distance(52_000), Distance(50_000), 6), race.celebration)
        val goal = api.take()
        assertEquals("PUT /api/v1/crews/5/goal", "${goal.method} ${goal.url.encodedPath}")
        assertEquals("""{"goalMeters":50000}""", goal.body!!.utf8())
    }
}
