package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
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
    setOf("SOCIAL_RUN", "DAWN"),
    "바람처럼",
    6,
    Distance(42_300),
    JoinPolicy.FREE
)
val crewB = CrewList.Item(
    12,
    "저녁런 클럽",
    null,
    setOf("EVENING", "BEGINNER_WELCOME"),
    "달빛",
    10,
    Distance(18_100),
    JoinPolicy.APPROVAL
)
val crewC = CrewList.Item(
    13,
    "길게 천천히",
    "LSD 위주",
    setOf("LONG_DISTANCE"),
    "거북",
    4,
    Distance(30_000),
    JoinPolicy.APPROVAL
)

val members = CrewMembers(
    listOf(
        CrewMembers.Member(1, "바람처럼", "LEADER", "매일 새벽 5시", "골드", "아침 참새"),
        CrewMembers.Member(2, "달빛", "MEMBER", null, "실버", "고독한 늑대"),
        CrewMembers.Member(3, "비공개러너", "MEMBER", "반가워요", null, null)
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

/** 토 07:00 KST 5km, 투표 중, 갈래요 2명(아직 내 응답 없음). */
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

val crewRunPosts = PostList(
    listOf(
        PostList.Item(
            31,
            PostList.Kind.CREW_RUN,
            "바람처럼",
            "같이 달려요",
            start.minusSeconds(3_600),
            mine = false,
            crewRun = crewRunVoting
        ),
        PostList.Item(
            32,
            PostList.Kind.CREW_RUN,
            "나",
            "10km LSD",
            start.minusSeconds(7_200),
            mine = true,
            crewRun = crewRunVoting.copy(
                distance = Distance(10_000),
                status = CrewRunStatus.FINISHED,
                goingCount = 3,
                myAnswer = VoteAnswer.GOING,
                finisherCount = 3,
                raceContribution = Distance(30_000)
            )
        )
    )
)

val cheers = PostList(
    listOf(
        PostList.Item(41, PostList.Kind.CHEER, "달빛", "이번 주 60km 가자!", start.minusSeconds(600), mine = false),
        PostList.Item(42, PostList.Kind.CHEER, "나", "내일 새벽 같이 뛰어요", start.minusSeconds(1_800), mine = true)
    )
)

val lobby = Lobby(
    31,
    listOf(
        Lobby.Participant("바람처럼", true, 1),
        Lobby.Participant("달빛", false, 2),
        Lobby.Participant("새벽곰", false, 7, me = true)
    ),
    meetAt = start.plusSeconds(600),
    distance = Distance(5_000)
)

/** 크루 11(새벽질주) 상세. 역할은 [role]. */
fun crewDetail(crewId: Long = 11, role: CrewRole? = CrewRole.MEMBER) = CrewDetail(
    crewId = crewId,
    name = if (crewId == 11L) "새벽질주" else "크루$crewId",
    intro = "새벽에 같이 달려요",
    tags = setOf("SOCIAL_RUN", "DAWN"),
    visibility = CrewVisibility.PUBLIC,
    joinPolicy = JoinPolicy.FREE,
    logoUrl = null,
    leaderId = 1,
    leaderName = "바람처럼",
    headcount = 6,
    weeklyDistance = Distance(42_300),
    myRole = role,
    inviteCode = if (role == CrewRole.LEADER) "K7P2QX" else null,
    inviteCodeExpiresAt = null
)

val crewRunResult = CrewRunResult(
    postId = 31,
    finishers = listOf(
        CrewRunResult.Finisher(2, "새벽곰", 1_710, 342, memberId = 7, me = true),
        CrewRunResult.Finisher(1, "바람처럼", 1_640, 328),
        CrewRunResult.Finisher(null, "달빛", null, null, quit = true, memberId = 2)
    ),
    firstCrewRun = true,
    raceContribution = Distance(13_400)
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
        answer("search:$keyword:${tags.sorted()}", CrewList(listOf(crewA, crewB, crewC), searchNext))
    } else {
        answer("search:$keyword:${tags.sorted()}:$cursor", CrewList(listOf(crewC.copy(crewId = 14, name = "넷째")), null))
    }

    override suspend fun recommend() = answer("recommend", CrewList(listOf(crewA)))

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

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period) = answer("ranking:$period", ranking)

    override suspend fun race(crewId: Long, weekStart: LocalDate?) = answer("race", race)

    override suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long?): Outcome<PostList> {
        if (cursor != null) {
            val more = PostList.Item(49, PostList.Kind.CHEER, "새내기", "반가워요", start.minusSeconds(90_000), false)
            return answer("posts:$kind:$cursor", PostList(listOf(more), null))
        }
        val first = if (kind == PostList.Kind.CHEER) cheers else crewRunPosts
        return answer("posts:$kind", first.copy(nextCursor = postsNext))
    }

    override suspend fun writeCheer(crewId: Long, body: String) = answer("cheer:$body", 43L)

    var lastCrewRun: CrewRunForm? = null

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> {
        lastCrewRun = form
        return answer("writeCrewRun:${form.distance.meters}", 33L)
    }

    override suspend fun deletePost(postId: Long) = answer("deletePost:$postId", Unit)

    override suspend fun vote(postId: Long, answer: VoteAnswer) = answer("vote:$postId:$answer", Unit)

    override suspend fun enterLobby(postId: Long) = answer("enter:$postId", lobbyState)

    override suspend fun toggleReady(postId: Long): Outcome<Lobby> {
        lobbyState = lobbyState.copy(
            participants = lobbyState.participants.map { if (it.me) it.copy(ready = !it.ready) else it }
        )
        return answer("ready:$postId", lobbyState)
    }

    override suspend fun leaveLobby(postId: Long) = answer("leaveLobby:$postId", lobbyState)

    override suspend fun quit(postId: Long) = answer("quit:$postId", Unit)

    override suspend fun crewRunResult(postId: Long) = answer("result:$postId", crewRunResult)
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

    override fun send(m: ProgressReport) {
        sent += m
    }

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

    override suspend fun removeLateHeartRates(recordId: String) = Outcome.Success(Unit)

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

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 실제 CrewRunCoordinator(+ RunTracker)를 가짜 포트로 조립한다. */
class CrewWorld(scope: CoroutineScope, val clock: MutableClock = MutableClock()) {
    val crews = FakeCrewRepository()
    val channels = FakeLiveChannels()
    private val silent = Silent()
    val tracker =
        RunTracker(NoPendingRuns(), silent, NoWatch(), silent, silent, silent, ConsentedSettings(), clock, scope)
    val coordinator = CrewRunCoordinator(tracker, channels, crews, clock, scope)

    companion object {
        fun TestScope.crewWorld(): CrewWorld =
            CrewWorld(CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext.job)))
    }
}
