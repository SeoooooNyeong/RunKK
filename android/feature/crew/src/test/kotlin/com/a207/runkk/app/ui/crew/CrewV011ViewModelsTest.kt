@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportStatus
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

/** 크루 홈 v0.11(FR-128, FR-138~FR-141, FR-145): 역할별 버튼, 공동 목표, 기여도 막대, 축하 카드, 대면 모임 응답. */
class CrewHomeV011ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val sharer = FakeCelebrationSharer()

    private fun vm(role: CrewRole = CrewRole.MEMBER, tab: CrewTab = CrewTab.RACE): CrewHomeViewModel {
        repo.role = role
        return CrewHomeViewModel(repo, ServerClock(MutableClock()), sharer, { _, _ ->
            Outcome.Success(Unit)
        }).also { it.bind(Routes.CrewHome(11), tab) }
    }

    @Test
    fun `권한표 - 크루 관리는 크루장만(W7 C5), 공동 목표는 크루장·운영진, 크루원은 없다(FR-128, 3·0·3)`() {
        assertFalse(vm(CrewRole.MEMBER).state.value.canManage)
        assertFalse(vm(CrewRole.MEMBER).state.value.canSetGoal)
        assertFalse(vm(CrewRole.MANAGER).state.value.canManage)
        assertTrue(vm(CrewRole.MANAGER).state.value.canSetGoal)
        assertFalse(vm(CrewRole.MANAGER).state.value.isLeader)
        assertTrue(vm(CrewRole.LEADER).state.value.isLeader)
    }

    @Test
    fun `알림으로 들어오면 그 탭을 먼저 연다(CREW_SCHEDULE)`() {
        val vm = vm(tab = CrewTab.SCHEDULE)
        assertEquals(CrewTab.SCHEDULE, vm.state.value.tab)
        assertTrue("posts:CREW_RUN" in repo.calls)
        assertTrue("race" !in repo.calls)
    }

    @Test
    fun `진행률은 100에서 자르고 기여도 막대는 거리 내림차순에 순위 숫자가 없다(FR-140, TC-7497)`() {
        assertEquals(100, goalProgressLabel(raceWithGoal.copy(goalProgressPercent = 117)))
        assertEquals(83, goalProgressLabel(raceWithGoal.copy(goalProgressPercent = 83)))
        assertEquals(listOf("달빛", "바람처럼", "나"), contributionBars(raceWithGoal).map { it.displayName })
        assertEquals(1f, contributionBars(raceWithGoal).first().fraction)
    }

    @Test
    fun `축하 카드를 공유한다(FR-141, TC-7496)`() {
        repo.fail.clear()
        val vm = vm()
        vm.onShareCelebration(raceWithGoal.celebration!!)
        assertEquals(listOf(raceWithGoal.celebration), sharer.shared)
        assertEquals(UiText.Res(R.string.crew_celebration_saved), vm.state.value.message)
    }

    @Test
    fun `기간을 빠르게 바꾸면 앞 요청을 취소해 늦게 온 이전 기간 순위가 덮지 않는다(A4)`() {
        val vm = vm()
        val monthly = CompletableDeferred<Unit>()
        repo.rankingGates[CrewRanking.Period.MONTHLY] = monthly
        repo.rankingByPeriod[CrewRanking.Period.MONTHLY] =
            CrewRanking(listOf(CrewRanking.Entry(1, "월간왕", Distance(90_000))))
        vm.onPeriod(CrewRanking.Period.MONTHLY)
        vm.onPeriod(CrewRanking.Period.WEEKLY)
        monthly.complete(Unit)
        assertEquals(CrewRanking.Period.WEEKLY, vm.state.value.period)
        assertEquals(ranking, vm.state.value.ranking)
    }

    @Test
    fun `일정 글 삭제·응답 뒤 다시 읽어도 이어 받은 뒤쪽 글이 남는다(A4)`() {
        repo.postsNext = 99
        // 크루런 탭은 크루런만 읽고 다음 쪽을 이어 받는다(피그마 F-11).
        val vm = vm(tab = CrewTab.SCHEDULE)
        vm.onLoadMore()
        assertTrue(vm.state.value.crewRuns!!.posts.any { it.postId == 49L })
        vm.onDeletePost(crewRunPosts.posts[0].copy(canDelete = true))
        assertTrue(vm.state.value.crewRuns!!.posts.any { it.postId == 49L }, "삭제 뒤 뒤쪽이 사라졌다")
        vm.onAttendance(crewRunPosts.posts[0], AttendanceAnswer.GOING)
        assertTrue(vm.state.value.crewRuns!!.posts.any { it.postId == 49L }, "응답 뒤 뒤쪽이 사라졌다")
        assertTrue(repo.calls.none { it.startsWith("posts:MEETUP") }, "대면 모임은 읽지 않는다")
    }

    @Test
    fun `삭제는 canDelete인 글만 묻는다(FR-213, TC-7459)`() {
        val vm = vm(tab = CrewTab.SCHEDULE)
        vm.onDeleteAsk(crewRunPosts.posts[0].copy(canDelete = false))
        assertNull(vm.state.value.confirmDelete)
        vm.onDeleteAsk(crewRunPosts.posts[0].copy(canDelete = true))
        assertEquals(crewRunPosts.posts[0].postId, vm.state.value.confirmDelete?.postId)
    }
}

class FakeCelebrationSharer : CelebrationSharer {
    val shared = mutableListOf<CelebrationCard>()

    override suspend fun share(card: CelebrationCard): UiText? {
        shared += card
        return UiText.Res(R.string.crew_celebration_saved)
    }
}

/**
 * 공지(FR-205)와 투표(FR-207) 중 옛 공지·투표 탭이 하던 일을 지금 맡은 ViewModel에서 본다: 게시판(알림으로 들어온 칩, 고정 한도,
 * 운영진 삭제), 공지 쓰기(코드 포인트 글자 수), 투표 화면(마감 응답).
 */
class CrewNoticePollMovedTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `앱 시계가 UTC여도 이번 주 월요일은 기기 시간대로 셈한다(MR !91 e2e 2 후속)`() {
        // 한국 11/09(월) 00:30 = UTC 11/08(일) 15:30. UTC로 세면 지난주 월요일 11/02가 된다
        val utc = MutableClock(java.time.Instant.parse("2026-11-08T15:30:00Z"), java.time.ZoneOffset.UTC)
        val vm = CrewGoalSetViewModel(repo, utc, seoul).also { it.bind(11) }
        assertEquals(java.time.LocalDate.of(2026, 11, 9), vm.state.value.weekStart)
    }

    @Test
    fun `공동 목표 거부는 서버 사유로 알린다(FR-138)`() {
        repo.fail["goal"] = AppError.Rejected("PERMISSION_DENIED")
        val vm = CrewGoalSetViewModel(repo, MutableClock()).also { it.bind(11) }
        vm.onQuick(30)
        vm.onSave()
        assertEquals(UiText.Res(R.string.crew_error_permission_denied), vm.state.value.error)
        assertFalse(vm.state.value.isSaved)
    }
}

/** 신고 대화상자(FR-214, D-149): 남의 글만, 사유 필수, 기타는 상세 1~200자. */
class CrewReportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val reports = FakeReportRepository()

    @Test
    fun `글 종류를 신고 대상 종류로 바꾸고 내 글은 열지 않는다`() {
        val vm = CrewReportViewModel(reports)
        vm.onAsk(cheers.posts[1])
        assertNull(vm.state.value.target, "내 글")
        vm.onAsk(crewRunPosts.posts[0])
        assertEquals(ReportTarget(ReportTargetKind.SCHEDULE, 31), vm.state.value.target)
        vm.onAsk(meetups.posts[0])
        assertEquals(ReportTarget(ReportTargetKind.SCHEDULE, 71), vm.state.value.target)
        vm.onAsk(notices().posts[0])
        assertEquals(ReportTarget(ReportTargetKind.NOTICE, 51), vm.state.value.target)
        vm.onAsk(polls.posts[0])
        assertEquals(ReportTarget(ReportTargetKind.POLL, 61), vm.state.value.target)
        vm.onAsk(boards.posts[0])
        assertEquals(ReportTarget(ReportTargetKind.BOARD, 81), vm.state.value.target)
        vm.onAsk(cheers.posts[0])
        assertEquals(ReportTarget(ReportTargetKind.CHEER, 41), vm.state.value.target)
    }

    @Test
    fun `기타는 상세 1~200자가 있어야 보내고 앞뒤 공백을 지운다`() {
        val vm = CrewReportViewModel(reports)
        vm.onAsk(boards.posts[0])
        assertFalse(vm.state.value.canSubmit)
        vm.onReason(ReportReason.OTHER)
        assertTrue(vm.state.value.isDetailMissing)
        vm.onDetail("가".repeat(250))
        assertEquals(200, vm.state.value.detail.length)
        vm.onDetail("  사진에 남의 얼굴이 나와요 ")
        vm.onSubmit()
        assertEquals(
            Triple(ReportTarget(ReportTargetKind.BOARD, 81), ReportReason.OTHER, "사진에 남의 얼굴이 나와요"),
            reports.calls.single()
        )
        assertNull(vm.state.value.target)
        assertEquals(UiText.Res(R.string.crew_report_done), vm.state.value.message)
    }

    @Test
    fun `이미 신고한 글은 ALREADY_REPORTED를 알린다(D-149)`() {
        reports.failure = AppError.Rejected("ALREADY_REPORTED")
        val vm = CrewReportViewModel(reports)
        vm.onAsk(boards.posts[0])
        vm.onReason(ReportReason.SPAM)
        vm.onSubmit()
        assertEquals(null, reports.calls.single().third)
        assertEquals(UiText.Res(R.string.crew_error_already_reported), vm.state.value.message)
    }
}

class FakeReportRepository : ReportRepository {
    val calls = mutableListOf<Triple<ReportTarget, ReportReason, String?>>()
    var failure: AppError? = null

    override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?): Outcome<ReportStatus> {
        calls += Triple(target, reason, detail)
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(ReportStatus.RECEIVED)
    }
}

/** 크루런 모집하기(FR-132, 피그마 F-12): 대면 모임은 만들지 않는다. */
class CrewRunWriteBodyViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    @Test
    fun `일정 내용 글자 수는 코드 포인트로 센다 - 이모지는 반으로 자르지 않는다(D-143)`() {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onBody(EMOJI.repeat(201))
        assertEquals(EMOJI.repeat(200), vm.state.value.body)
    }
}

/** UTF-16 두 칸을 쓰는 글자 하나(코드 포인트 1개, D-143). */
private const val EMOJI = "\uD83C\uDFC3"
