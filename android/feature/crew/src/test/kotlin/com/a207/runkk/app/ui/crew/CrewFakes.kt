package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.BoardPost
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
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
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.Notice
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.Poll
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** 끝나지 않는 ViewModel 작업(1초 시계)이 runTest 정리를 막지 않게 시험 끝에 범위를 닫는다. */
class ViewModelCloser {
    private val opened = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(vm: T): T = vm.also { opened += it }

    fun closeAll() = opened.forEach { it.viewModelScope.cancel() }
}

fun MainDispatcherRule.runVmTest(body: suspend TestScope.(ViewModelCloser) -> Unit) = runTest(dispatcher) {
    val closer = ViewModelCloser()
    try {
        body(closer)
    } finally {
        closer.closeAll()
    }
}

/** 2026-10-20 19:00 KST(10:00 UTC), 화요일. */
val start: Instant = Instant.parse("2026-10-20T10:00:00Z")
val seoul: ZoneId = ZoneId.of("Asia/Seoul")

class MutableClock(var now: Instant = start, private val zone: ZoneId = seoul) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

val crewA = CrewList.Item(
    11,
    "새벽질주",
    "새벽에 같이 달려요",
    setOf("WEEKDAY_EVENING", "WEEKDAY_DAWN"),
    "바람처럼",
    6,
    Distance(42_300),
    JoinPolicy.FREE
)
val crewB = CrewList.Item(
    12,
    "저녁런 클럽",
    null,
    setOf("WEEKDAY_EVENING", "WEEKEND_MORNING"),
    "달빛",
    10,
    Distance(18_100),
    JoinPolicy.APPROVAL
)
val crewC = CrewList.Item(
    13,
    "길게 천천히",
    "LSD 위주",
    setOf("WEEKEND_LUNCH"),
    "거북",
    4,
    Distance(30_000),
    JoinPolicy.APPROVAL
)

val members = CrewMembers(
    listOf(
        CrewMembers.Member(1, "바람처럼", CrewRole.LEADER, "매일 새벽 5시", "골드", "MORNING_SPARROW"),
        CrewMembers.Member(2, "달빛", CrewRole.MEMBER, null, "실버", "LONE_WOLF"),
        CrewMembers.Member(3, "비공개러너", CrewRole.MEMBER, "반가워요", null, null)
    )
)

val race = RaceStatus(
    total = Distance(58_600),
    closesAt = Instant.parse("2026-10-25T15:00:00Z"),
    contributors = listOf(
        RaceStatus.Contributor("바람처럼", Distance(18_200), 0.31),
        RaceStatus.Contributor("나", Distance(12_900), 0.22)
    ),
    myShare = 0.22
)

val ranking = CrewRanking(
    listOf(
        CrewRanking.Entry(1, "바람처럼", Distance(18_200)),
        CrewRanking.Entry(2, "나", Distance(12_900)),
        CrewRanking.Entry(3, "달빛", Distance(10_400))
    )
)

/** 토 07:00 KST 5km, 투표 중, 참석 2명(아직 내 응답 없음). */
val crewRunVoting = PostList.CrewRunInfo(
    distance = Distance(5_000),
    scheduledAt = Instant.parse("2026-10-23T22:00:00Z"),
    voteDeadline = null,
    status = CrewRunStatus.VOTING,
    goingCount = 2,
    myAnswer = null,
    finisherCount = 0,
    raceContribution = null
)

/** 일정 상세(W6): 토 07:00 KST 제한 없음, 매주 반복, 작성자 "바람처럼"과 "달빛"이 참석, 나는 아직 응답 없음. */
val crewRunDetail = CrewRunDetail(
    postId = 42,
    crewId = 11,
    title = "토요일 한강 같이 뛰어요",
    body = "반포 한강공원 8번 출구, 6분대 페이스",
    authorName = "바람처럼",
    isMine = false,
    writtenAt = Instant.parse("2026-10-21T01:00:00Z"),
    scheduledAt = Instant.parse("2026-10-23T22:00:00Z"),
    distance = null,
    status = CrewRunStatus.VOTING,
    goingCount = 2,
    myAnswer = null,
    canEdit = false,
    canDelete = false,
    repeat = CrewRunRepeat.WEEKLY,
    repeatDay = java.time.DayOfWeek.SATURDAY,
    attendees = listOf(
        CrewRunDetail.Attendee(1, "바람처럼", isAuthor = true, isMe = false),
        CrewRunDetail.Attendee(2, "달빛", isAuthor = false, isMe = false)
    )
)

val crewRunPosts = PostList(
    listOf(
        PostList.Item(
            31,
            PostList.Kind.CREW_RUN,
            "바람처럼",
            "같이 달려요",
            start.minusSeconds(3_600),
            isMine = false,
            crewRun = crewRunVoting
        ),
        PostList.Item(
            32,
            PostList.Kind.CREW_RUN,
            "나",
            "10km LSD",
            start.minusSeconds(7_200),
            isMine = true,
            canDelete = true,
            crewRun = crewRunVoting.copy(
                distance = Distance(10_000),
                status = CrewRunStatus.FINISHED,
                goingCount = 3,
                myAnswer = AttendanceAnswer.GOING,
                finisherCount = 3,
                raceContribution = Distance(30_000)
            )
        )
    )
)

val cheers = PostList(
    listOf(
        PostList.Item(41, PostList.Kind.CHEER, "달빛", "이번 주 60km 가자!", start.minusSeconds(600), isMine = false),
        PostList.Item(
            42,
            PostList.Kind.CHEER,
            "나",
            "내일 새벽 같이 뛰어요",
            start.minusSeconds(1_800),
            isMine = true,
            canDelete = true
        )
    )
)

/** 공지 3건: 51·52 고정, 53 고정 아님(FR-205). 운영진 이상이면 canDelete. */
fun notices(canDelete: Boolean = false) = PostList(
    listOf(
        PostList.Item(
            51,
            PostList.Kind.NOTICE,
            "바람처럼",
            "토요일 정기런은 7시 반포",
            start.minusSeconds(600),
            false,
            canDelete = canDelete,
            notice = Notice(true)
        ),
        PostList.Item(
            52,
            PostList.Kind.NOTICE,
            "바람처럼",
            "회비 안내",
            start.minusSeconds(1_200),
            false,
            canDelete = canDelete,
            notice = Notice(true)
        ),
        PostList.Item(
            53,
            PostList.Kind.NOTICE,
            "달빛",
            "우천 시 취소",
            start.minusSeconds(1_800),
            false,
            canDelete = canDelete,
            notice = Notice(false)
        )
    )
)

/** 투표 2건: 61 진행 중 선택지(내 응답 없음), 62 마감 찬반(내 응답 1). */
val polls = PostList(
    listOf(
        PostList.Item(
            61,
            PostList.Kind.POLL,
            "달빛",
            "다음 달 대회 어디 나갈까요?",
            start.minusSeconds(600),
            false,
            poll = Poll(PollMethod.CHOICES, start.plusSeconds(86_400), false, 3, null)
        ),
        PostList.Item(
            62,
            PostList.Kind.POLL,
            "나",
            "회식 할까요?",
            start.minusSeconds(90_000),
            true,
            canDelete = true,
            poll = Poll(PollMethod.YES_NO, start.minusSeconds(3_600), true, 5, 1)
        )
    )
)

/** 대면 모임 1건(정원 8, 참석 2·불참 1·미정 0, 내 응답 없음). */
val meetups = PostList(
    listOf(
        PostList.Item(
            71,
            PostList.Kind.MEETUP,
            "바람처럼",
            "번개 모임 후 맥주",
            start.minusSeconds(600),
            false,
            meetup = Meetup(start.plusSeconds(172_800), "여의나루역 2번 출구", 8, 2, 1, 0, null)
        )
    )
)

/** 게시판 글 2건: 81 사진 2장(남의 글), 82 글만(내 글). */
val boards = PostList(
    listOf(
        PostList.Item(
            81,
            PostList.Kind.BOARD,
            "달빛",
            "오늘 한강 노을 미쳤다",
            start.minusSeconds(600),
            false,
            board = BoardPost(
                "오늘 한강 노을 미쳤다",
                listOf(BoardPost.Photo(901, "/api/v1/crew-photos/901"), BoardPost.Photo(902, "/api/v1/crew-photos/902"))
            )
        ),
        PostList.Item(
            82,
            PostList.Kind.BOARD,
            "나",
            "신발 추천 받아요",
            start.minusSeconds(3_600),
            true,
            canDelete = true,
            board = BoardPost("신발 추천 받아요", emptyList())
        )
    )
)

/** 목표 50km를 넘긴 주: 진행률 100(상한), 축하 카드. */
val raceWithGoal = race.copy(
    contributors = listOf(
        RaceStatus.Contributor("바람처럼", Distance(18_200), 0.31, memberId = 1),
        RaceStatus.Contributor("나", Distance(12_900), 0.22, memberId = 7, isMe = true),
        RaceStatus.Contributor("달빛", Distance(27_500), 0.47, memberId = 2)
    ),
    goal = Distance(50_000),
    goalProgressPercent = 100,
    achievedAt = start.minusSeconds(3_600),
    celebration = CelebrationCard("새벽질주", Distance(58_600), Distance(50_000), 3)
)

val lobby = Lobby(
    31,
    listOf(
        Lobby.Participant("바람처럼", true, 1, faceKey = "default:FOX", shownGender = Gender.FEMALE),
        Lobby.Participant("달빛", false, 2, shownGender = Gender.MALE),
        Lobby.Participant("새벽곰", false, 7, isMe = true)
    ),
    meetAt = start.plusSeconds(600),
    distance = Distance(5_000)
)

/** 크루 11(새벽질주) 상세. 역할은 [role]. */
fun crewDetail(crewId: Long = 11, role: CrewRole? = CrewRole.MEMBER) = CrewDetail(
    crewId = crewId,
    name = if (crewId == 11L) "새벽질주" else "크루$crewId",
    intro = "새벽에 같이 달려요",
    tags = setOf("WEEKDAY_EVENING", "WEEKDAY_DAWN"),
    visibility = CrewVisibility.PUBLIC,
    joinPolicy = JoinPolicy.FREE,
    logoUrl = null,
    leaderId = 1,
    leaderName = "바람처럼",
    headcount = 6,
    weeklyDistance = Distance(42_300),
    myRole = role,
    inviteCode = if (role != null) "K7P2QX" else null,
    inviteCodeExpiresAt = null
)

val crewRunResult = CrewRunResult(
    postId = 31,
    participants = listOf(
        CrewRunResult.Participant(
            "바람처럼",
            Distance(5_280),
            1_712,
            324,
            memberId = 1,
            faceKey = "default:FOX",
            shownGender = Gender.FEMALE
        ),
        CrewRunResult.Participant("새벽곰", Distance(4_870), 1_668, 342, memberId = 7, isMe = true),
        CrewRunResult.Participant(
            "달빛",
            Distance(4_620),
            1_700,
            368,
            memberId = 2,
            faceKey = "selfie:face/2/set-a",
            shownGender = Gender.MALE
        ),
        CrewRunResult.Participant("거북", Distance(4_950), 1_790, 362, memberId = 3),
        CrewRunResult.Participant(
            "한강토끼",
            Distance(3_720),
            1_500,
            403,
            memberId = 4,
            faceKey = "default:BEAR",
            shownGender = Gender.FEMALE
        )
    ),
    totalDistance = Distance(23_440),
    isFirstCrewRun = true,
    raceContribution = Distance(23_440),
    status = CrewRunStatus.FINISHED,
    title = "오늘 저녁 한강 5km",
    distance = Distance(5_000),
    myRecordId = "rec-31"
)

/** 크루 REST 가짜. 부른 연산을 [calls]에 남긴다. 결과는 [fail]에 코드로 넣으면 그 연산이 거부된다. */
class FakeCrewRepository : CrewRepository {
    val calls = mutableListOf<String>()
    val fail = mutableMapOf<String, AppError>()
    var lobbyState = lobby

    /** GET /crews/me 응답. 가입되면(JOINED) 그 크루로 바뀐다. */
    var mine = MyCrew(null, emptyList())

    /** 크루 상세의 내 역할. */
    var role: CrewRole? = CrewRole.MEMBER

    /** 첫 쪽 다음 커서(크루 목록·게시글). null이면 한 쪽뿐이다. */
    var searchNext: Long? = null
    var postsNext: Long? = null

    /** 다음 쪽 응답의 다음 커서(기본 끝). 같은 커서를 넣으면 서버가 같은 쪽을 되풀이하는 상황이다. */
    var cursorPageNext: Long? = null

    /** 첫 쪽 공개 크루. */
    var searchFirstPage = listOf(crewA, crewB, crewC)

    override suspend fun myCrew() = answer("myCrew", mine)

    /** 정보 수정(changeInfo)이 받아들인 크루명. 크루 조회가 이 이름을 돌려준다. */
    private var renamed: String? = null

    override suspend fun detail(crewId: Long) =
        answer("detail:$crewId", crewDetail(crewId, role).let { d -> renamed?.let { d.copy(name = it) } ?: d })

    private fun <T> answer(op: String, value: T): Outcome<T> {
        calls += op
        return fail[op.substringBefore(':')]?.let { Outcome.Failure(it) } ?: Outcome.Success(value)
    }

    override suspend fun create(form: CrewForm) = answer("create:${form.name}", 77L)

    override suspend fun changeInfo(crewId: Long, form: CrewForm) =
        answer("changeInfo:${form.name}", Unit).also { if (it is Outcome.Success) renamed = form.name }

    override suspend fun reissueInviteCode(crewId: Long) = answer("reissue", "K7P2QX")

    override suspend fun transferLeadership(crewId: Long, memberId: Long) = answer("transfer:$memberId", Unit)

    override suspend fun delete(crewId: Long) = answer("delete", Unit)

    override suspend fun search(keyword: String?, tags: Set<String>, cursor: Long?) = if (cursor == null) {
        answer("search:$keyword:${tags.sorted()}", CrewList(searchFirstPage, searchNext))
    } else {
        answer("search:$keyword:${tags.sorted()}:$cursor", CrewList(listOf(crewC.copy(crewId = 14, name = "넷째")), null))
    }

    /** 자유 가입 크루(11)는 바로 가입되고 나머지는 승인 대기(신청 91)다. */
    override suspend fun apply(crewId: Long): Outcome<JoinResult> {
        val result = if (crewId == 11L) {
            JoinResult(JoinResult.Status.JOINED, crewId, null)
        } else {
            JoinResult(JoinResult.Status.PENDING, crewId, 91)
        }
        return answer("apply:$crewId", result).also { if (it is Outcome.Success) reflect(it.value) }
    }

    /** 초대 코드는 크루 12에 바로 가입된다. */
    override suspend fun applyWithInviteCode(code: String) =
        answer("invite:$code", JoinResult(JoinResult.Status.JOINED, 12, null)).also {
            if (it is Outcome.Success) reflect(it.value)
        }

    private fun reflect(result: JoinResult) {
        mine = when (result.status) {
            JoinResult.Status.JOINED -> MyCrew(crewDetail(result.crewId, CrewRole.MEMBER), emptyList())

            JoinResult.Status.PENDING -> mine.copy(
                pendingRequests = mine.pendingRequests +
                    MyCrew.PendingJoin(result.requestId ?: 0, result.crewId, "크루${result.crewId}", start)
            )

            JoinResult.Status.CANCELLED -> mine
        }
    }

    override suspend fun cancelJoinRequest(requestId: Long) = answer("cancelJoin:$requestId", Unit)

    override suspend fun approveJoin(requestId: Long) = answer("approve:$requestId", Unit)

    override suspend fun rejectJoin(requestId: Long) = answer("reject:$requestId", Unit)

    override suspend fun leave(crewId: Long) = answer("leave", Unit)

    override suspend fun kick(crewId: Long, memberId: Long) = answer("kick:$memberId", Unit)

    override suspend fun editCrewProfile(crewId: Long, intro: String) = answer("intro:$intro", Unit)

    override suspend fun members(crewId: Long) = answer("members", members)

    override suspend fun pendingRequests(crewId: Long) =
        answer("pending", PendingRequests(listOf(PendingRequests.Request(91, 9, "새내기", start.minusSeconds(86_400)))))

    /** 기간별 순위 응답(없으면 [ranking])과 응답을 붙잡아 둘 문(시험이 complete로 연다). */
    val rankingByPeriod = mutableMapOf<CrewRanking.Period, CrewRanking>()
    val rankingGates = mutableMapOf<CrewRanking.Period, kotlinx.coroutines.CompletableDeferred<Unit>>()

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking> {
        rankingGates[period]?.await()
        return answer("ranking:$period", rankingByPeriod[period] ?: ranking)
    }

    override suspend fun race(crewId: Long, weekStart: LocalDate?) = answer("race", race)

    override suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long?): Outcome<PostList> {
        if (cursor != null) {
            val more = PostList.Item(49, PostList.Kind.CHEER, "새내기", "반가워요", start.minusSeconds(90_000), false)
            return answer("posts:$kind:$cursor", PostList(listOf(more), cursorPageNext))
        }
        val first = when (kind) {
            PostList.Kind.CHEER -> cheers

            PostList.Kind.NOTICE -> notices(canDelete = role == CrewRole.LEADER || role == CrewRole.MANAGER)

            PostList.Kind.POLL -> polls

            PostList.Kind.MEETUP -> meetups

            PostList.Kind.BOARD -> boards

            // 전체(kind 없음): 일정 글과 게시판 글이 섞여 온다.
            null -> PostList(crewRunPosts.posts + notices().posts + polls.posts + cheers.posts + boards.posts)

            else -> crewRunPosts
        }
        val answered = first.posts.map { post ->
            val mine = attendance[post.postId] ?: return@map post
            post.copy(crewRun = post.crewRun?.copy(myAnswer = mine), meetup = post.meetup?.copy(myAnswer = mine))
        }
        return answer("posts:$kind", PostList(answered, postsNext))
    }

    var lastCrewRun: CrewRunForm? = null

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> {
        lastCrewRun = form
        return answer("writeCrewRun:${form.distance?.meters}", 33L)
    }

    override suspend fun deletePost(postId: Long) = answer("deletePost:$postId", Unit)

    /** 남긴 일정 응답. 다음 목록 조회가 내 응답으로 돌려준다. */
    private val attendance = mutableMapOf<Long, AttendanceAnswer>()

    override suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer) =
        answer("vote:$postId:$answer", Unit).also { if (it is Outcome.Success) attendance[postId] = answer }

    /** 참석 취소(W6): 응답을 거둔다. */
    override suspend fun withdrawAttendance(postId: Long) =
        answer("withdraw:$postId", Unit).also { if (it is Outcome.Success) attendance.remove(postId) }

    /** 상세가 돌려주는 일정(W6). 참석·취소를 반영한다. */
    var detailState = crewRunDetail

    var lastEdit: CrewRunEdit? = null

    override suspend fun crewRun(postId: Long) = answer(
        "crewRun:$postId",
        detailState.copy(postId = postId, myAnswer = attendance[postId] ?: detailState.myAnswer)
    )

    override suspend fun editCrewRun(postId: Long, edit: CrewRunEdit): Outcome<CrewRunDetail> {
        lastEdit = edit
        val changed = detailState.copy(
            title = edit.title,
            body = edit.body.orEmpty(),
            distance = edit.distance,
            repeat = edit.repeat
        )
        return answer("editCrewRun:$postId", changed).also { if (it is Outcome.Success) detailState = changed }
    }

    override suspend fun appointManager(crewId: Long, memberId: Long) = answer("appoint:$memberId", Unit)

    override suspend fun dismissManager(crewId: Long, memberId: Long) = answer("dismiss:$memberId", Unit)

    override suspend fun setWeeklyGoal(crewId: Long, goal: Distance) =
        answer("goal:${goal.meters}", raceWithGoal.copy(goal = goal))

    override suspend fun enterLobby(postId: Long) = answer("enter:$postId", lobbyState)

    override suspend fun toggleReady(postId: Long): Outcome<Lobby> {
        lobbyState = lobbyState.copy(
            participants = lobbyState.participants.map { if (it.isMe) it.copy(isReady = !it.isReady) else it }
        )
        return answer("ready:$postId", lobbyState)
    }

    override suspend fun leaveLobby(postId: Long) = answer("leaveLobby:$postId", lobbyState)

    override suspend fun quit(postId: Long, distanceMeters: Long?, elapsed: Duration?) =
        answer("quit:$postId:${distanceMeters ?: "-"}", Unit)

    override suspend fun pause(postId: Long, elapsed: Duration) = answer("pause:$postId:${elapsed.seconds}", Unit)

    var resultState = crewRunResult

    override suspend fun crewRunResult(postId: Long) = answer("result:$postId", resultState)

    /** 셀카 얼굴 키면 부위 그림 한 장, 아니면 빈 목록(FaceImageSource와 같은 규칙). */
    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = answer(
        "face:$faceKey",
        if (faceKey?.startsWith("selfie:") == true) listOf(FaceLayer(FacePart.EYES, byteArrayOf(1))) else emptyList()
    )
}

class FakeLiveChannels : LiveChannelProvider {
    val channel = FakeLiveChannel()

    override fun open(): LiveChannel = channel
}

class FakeLiveChannel : LiveChannel {
    val sent = mutableListOf<ProgressReport>()
    private val flow = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)

    var connectFailure: AppError? = null

    override suspend fun connect(): Outcome<Unit> = connectFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)

    override fun send(m: ClientMessage) {
        if (m is ProgressReport) sent += m
    }

    override val state: StateFlow<LiveState> = MutableStateFlow(LiveState.CONNECTED)

    override val messages: Flow<ServerMessage> = flow

    var closed = 0

    override fun close() {
        closed++
    }

    fun push(m: ServerMessage) {
        check(flow.tryEmit(m))
    }
}

private class NoPendingRuns : PendingRunRepository {
    val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        runs.value = runs.value + run
        return Outcome.Success(Unit)
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.value)

    override fun observePending(): Flow<List<CompletedRun>> = runs

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates) = Outcome.Success(Unit)

    override suspend fun clear() = Outcome.Success(Unit)
}

private class Silent :
    GuidanceOutput,
    UploadScheduler,
    MeasurementHost,
    LocationPermission {
    override fun play(g: Guidance) = Unit

    override fun requestUpload() = Unit

    override fun startForeground(): Outcome<Unit> = Outcome.Success(Unit)

    override fun stop() = Unit

    override fun granted() = true
}

private class NoWatch : WatchGateway {
    override val endRequests: Flow<Unit> = MutableSharedFlow()
    override val pauseRequests: Flow<WatchGateway.PauseAction> = MutableSharedFlow()

    override fun publishProximity(alert: ProximityAlert?) = Unit

    override fun publishLeadChange(kind: Guidance.Kind) = Unit

    override val startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val heartRates: Flow<List<HeartRateSample>> = MutableSharedFlow()

    override fun publish(d: WatchDisplay) = Unit

    override fun isConnected() = false
}

private class ConsentedSettings : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 실제 CrewRunCoordinator(+ RunTracker)를 가짜 포트로 조립한다. */
class CrewWorld(scope: CoroutineScope, val clock: MutableClock = MutableClock()) {
    val crews = FakeCrewRepository()
    val channels = FakeLiveChannels()
    private val silent = Silent()
    val tracker =
        RunTracker(
            RunArchive(NoPendingRuns(), silent, scope),
            RunWatchBridge(NoWatch(), scope),
            RunCoach(silent, ConsentedSettings()),
            MeasurementRuntime(silent, silent, ConsentedSettings(), clock)
        )
    val coordinator = CrewRunCoordinator(tracker, channels, crews, clock, scope)

    companion object {
        fun TestScope.crewWorld(): CrewWorld =
            CrewWorld(CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext.job)))
    }
}
