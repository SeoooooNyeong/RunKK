package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.Album
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.BoardPost
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.MeetupForm
import com.a207.runkk.app.domain.crew.Notice
import com.a207.runkk.app.domain.crew.Poll
import com.a207.runkk.app.domain.crew.PollForm
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PollResult
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import retrofit2.create

/**
 * api.crew v0.11(F.2.2, F.2.4, D-98~D-100, D-105, D-114): 새 게시 종류 해독, 시각 두 형식, 운영진·공지·투표·대면 모임·게시판·앨범·
 * 공동 목표. 서버 S3a 구현 전이라 05-03·S0 DTO 기준이다.
 */
class CrewBoardAndScheduleTest {
    private val api = ApiTestServer()
    private val photos = FakePhotos()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), photos.compressor)

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
    fun `공지·투표·대면 모임은 종류별 항목을 싣고 시각은 오프셋을 붙인다`() = runTest {
        repeat(4) { api.reply("""{"id":${it + 100}}""", code = 201) }

        repository.writeNotice(5, "정기런 공지", isPinned = true).value()
        val closesAt = Instant.parse("2026-10-27T15:00:00Z")
        repository.writePoll(5, PollForm("찬성?", PollMethod.YES_NO, listOf("무시"), closesAt)).value()
        repository.writePoll(5, PollForm("언제?", PollMethod.CHOICES, listOf("토", "일"), closesAt)).value()
        repository.writeMeetup(5, MeetupForm("모여요", Instant.parse("2026-10-27T10:00:00Z"), "여의나루", null)).value()

        fun body() = Json.parseToJsonElement(api.take().body!!.utf8())
        assertEquals(Json.parseToJsonElement("""{"kind":"NOTICE","body":"정기런 공지","pinned":true}"""), body())
        assertEquals(
            Json.parseToJsonElement(
                """{"kind":"POLL","body":"찬성?","pollMethod":"YES_NO","closesAt":"2026-10-28T00:00:00+09:00"}"""
            ),
            body(),
            "YES_NO는 선택지를 보내지 않는다"
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"kind":"POLL","body":"언제?","pollMethod":"CHOICES","options":["토","일"],"closesAt":"2026-10-28T00:00:00+09:00"}"""
            ),
            body()
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"kind":"MEETUP","body":"모여요","scheduledAt":"2026-10-27T19:00:00+09:00","placeName":"여의나루"}"""
            ),
            body()
        )
    }

    @Test
    fun `공지 고정·투표 응답·투표 결과`() = runTest {
        api.reply("""{"postId":4,"pinned":false}""")
        val poll =
            """{"postId":5,"question":"언제?","method":"CHOICES","closesAt":"2026-10-28T00:00:00+09:00","closed":false,
               |"options":[{"optionNo":1,"label":"토","count":1,"respondents":[{"memberId":3,"displayName":"곰"}]},
               |{"optionNo":2,"label":"일","count":0,"respondents":[]}],"myOptionNo":1}
            """.trimMargin()
        api.reply(poll)
        api.reply(poll)
        api.reply("""{"code":"POLL_CLOSED","message":"마감된 투표"}""", code = 409)

        assertEquals(Outcome.Success(Unit), repository.pin(4, isPinned = false))
        assertEquals(Outcome.Success(Unit), repository.respondPoll(5, 1))
        val result = repository.pollResult(5).value()
        assertEquals(Outcome.Failure(AppError.Rejected("POLL_CLOSED")), repository.respondPoll(5, 2))

        assertEquals(
            PollResult(
                5,
                "언제?",
                PollMethod.CHOICES,
                Instant.parse("2026-10-27T15:00:00Z"),
                false,
                listOf(
                    PollResult.Option(1, "토", 1, listOf(PollResult.Respondent(3, "곰"))),
                    PollResult.Option(2, "일", 0, emptyList())
                ),
                1
            ),
            result
        )
        val pin = api.take()
        assertEquals("PUT /api/v1/posts/4/pin", "${pin.method} ${pin.url.encodedPath}")
        assertEquals("""{"pinned":false}""", pin.body!!.utf8())
        val respond = api.take()
        assertEquals("PUT /api/v1/posts/5/poll-response", "${respond.method} ${respond.url.encodedPath}")
        assertEquals("""{"optionNo":1}""", respond.body!!.utf8())
        assertEquals("GET /api/v1/posts/5/poll", path())
    }

    @Test
    fun `TC-6518 게시판 글은 본문과 줄인 사진 5장을 multipart 요청 하나로 보낸다`() = runTest {
        (1..5).forEach { photos.originals["content://photo/$it"] = FakePhotos.jpeg(10 * 1024 * 1024) }
        photos.encodedSize = { _, _ -> 1_000_000 }
        api.reply("""{"id":300}""", code = 201)

        val id = repository.writeBoard(5, "글", (1..5).map { "content://photo/$it" })

        assertEquals(Outcome.Success(300L), id)
        assertEquals(1, api.server.requestCount)
        val request = api.take()
        assertEquals("POST /api/v1/crews/5/board-posts", "${request.method} ${request.url.encodedPath}")
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val raw = request.body!!.utf8()
        assertEquals(5, Regex("name=\"photos\"").findAll(raw).count(), "사진 파트 5개")
        assertEquals(1, Regex("name=\"body\"").findAll(raw).count())
        assertTrue(request.body!!.size <= 6L * 1024 * 1024, "요청 전체 6MB 이하: ${request.body!!.size}")
    }

    @Test
    fun `게시판 사진 하나라도 줄이지 못하면 보내지 않고 거부를 돌려준다`() = runTest {
        photos.originals["content://ok"] = FakePhotos.jpeg(1_000)
        photos.originals["content://big"] = FakePhotos.jpeg(10_485_761)

        val result = repository.writeBoard(5, "글", listOf("content://ok", "content://big"))

        assertEquals(Outcome.Failure(AppError.Rejected("TOO_LARGE")), result)
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `AN5-C-10 사진 압축은 주입한 디스패처에서 한다`() = runTest {
        photos.originals["content://photo/1"] = FakePhotos.jpeg(2_000)
        api.reply("""{"id":301}""", code = 201)
        val io = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val injected = CrewRepositoryImpl(api.retrofit.create(), photos.compressor, io)

        val id = injected.writeBoard(5, "글", listOf("content://photo/1"))

        assertEquals(Outcome.Success(301L), id)
        assertTrue(io.dispatches > 0, "주입한 디스패처로 옮겨 압축한다")
    }

    /** 몇 번 옮겨 실행했는지 센다. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun `앨범과 공동 목표`() = runTest {
        api.reply(
            """{"photos":[{"photoId":61,"postId":6,"url":"/api/v1/crew-photos/61","postedAt":"2026-10-25T07:00:00+09:00"}],
               |"nextCursor":61}
            """.trimMargin()
        )
        api.reply(
            """{"weekStart":"2026-10-19","totalMeters":52000,"closesAt":"2026-10-26T00:00:00+09:00","contributors":[],
               |"goalMeters":50000,"goalProgressPercent":100,"achievedAt":"2026-10-25T08:00:00+09:00",
               |"celebration":{"crewName":"새벽런","totalMeters":52000,"goalMeters":50000,"participantCount":6}}
            """.trimMargin()
        )

        val album = repository.album(5, null).value()
        val race = repository.setWeeklyGoal(5, Distance(50_000)).value()

        assertEquals(
            Album(listOf(Album.Photo(61, 6, "/api/v1/crew-photos/61", Instant.parse("2026-10-24T22:00:00Z"))), 61),
            album
        )
        assertEquals(Distance(50_000), race.goal)
        assertEquals(100, race.goalProgressPercent)
        assertEquals(Instant.parse("2026-10-24T23:00:00Z"), race.achievedAt)
        assertEquals(CelebrationCard("새벽런", Distance(52_000), Distance(50_000), 6), race.celebration)
        assertEquals("/api/v1/crews/5/album", api.take().url.encodedPath)
        val goal = api.take()
        assertEquals("PUT /api/v1/crews/5/goal", "${goal.method} ${goal.url.encodedPath}")
        assertEquals("""{"goalMeters":50000}""", goal.body!!.utf8())
    }

    @Test
    fun `게시판 사진은 인증된 연결로 crew-photos에서 바이트를 받고 너무 크면 받지 않는다(FR-212, D-100)`() = runTest {
        api.reply("JPEGDATA")
        api.reply("x".repeat(2 * 1024 * 1024 + 1))

        val bytes = repository.photo(61).value()
        val tooLarge = repository.photo(62)

        assertEquals("JPEGDATA", bytes.decodeToString())
        assertEquals(Outcome.Failure(AppError.Rejected("TOO_LARGE")), tooLarge)
        assertEquals("GET /api/v1/crew-photos/61", path())
        assertEquals("GET /api/v1/crew-photos/62", path())
    }
}
