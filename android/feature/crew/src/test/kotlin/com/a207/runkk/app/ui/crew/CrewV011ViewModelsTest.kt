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
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportStatus
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import java.time.Instant
import java.time.LocalTime
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
        return CrewHomeViewModel(repo, ServerClock(MutableClock()), sharer).also { it.bind(Routes.CrewHome(11), tab) }
    }

    @Test
    fun `권한표 - 크루 관리·공동 목표는 크루장·운영진만, 크루원은 없다(FR-128, 3·0·3)`() {
        assertFalse(vm(CrewRole.MEMBER).state.value.canManage)
        assertFalse(vm(CrewRole.MEMBER).state.value.canSetGoal)
        assertTrue(vm(CrewRole.MANAGER).state.value.canManage)
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
    fun `레이스 탭은 고정 공지(최대 3)를 함께 읽는다(FR-205, 와이어 E2)`() {
        val vm = vm()
        assertTrue("posts:NOTICE" in repo.calls)
        assertEquals(listOf(51L, 52L), vm.state.value.pinnedNotices.map { it.postId })
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
    fun `일정 탭의 대면 모임은 처음 고를 때 읽고 참석·불참·미정 응답 뒤 다시 읽는다(FR-145)`() {
        val vm = vm(tab = CrewTab.SCHEDULE)
        vm.onScheduleKind(PostList.Kind.MEETUP)
        assertEquals(1, repo.calls.count { it == "posts:MEETUP" })
        assertEquals("번개 모임 후 맥주", vm.state.value.meetups?.posts?.single()?.summary)
        vm.onAttendance(meetups.posts[0], AttendanceAnswer.UNDECIDED)
        assertTrue("vote:71:UNDECIDED" in repo.calls)
        assertEquals(AttendanceAnswer.UNDECIDED, vm.state.value.meetups?.posts?.single()?.meetup?.myAnswer)
        assertEquals(2, repo.calls.count { it == "posts:MEETUP" })
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
        // 일정 탭은 크루런과 대면 모임을 함께 읽고, 다음 쪽은 크루런부터 이어 받는다(와이어 I4).
        val vm = vm(tab = CrewTab.SCHEDULE)
        vm.onLoadMore()
        vm.onLoadMore()
        assertTrue(vm.state.value.meetups!!.posts.any { it.postId == 49L })
        vm.onDeletePost(meetups.posts[0].copy(canDelete = true))
        assertTrue(vm.state.value.meetups!!.posts.any { it.postId == 49L }, "삭제 뒤 뒤쪽이 사라졌다")
        vm.onAttendance(meetups.posts[0], AttendanceAnswer.GOING)
        assertTrue(vm.state.value.meetups!!.posts.any { it.postId == 49L }, "응답 뒤 뒤쪽이 사라졌다")
    }

    @Test
    fun `삭제는 canDelete인 글만 묻는다(FR-213, TC-7459)`() {
        val vm = vm(tab = CrewTab.SCHEDULE)
        vm.onDeleteAsk(meetups.posts[0].copy(canDelete = false))
        assertNull(vm.state.value.confirmDelete)
        vm.onDeleteAsk(meetups.posts[0].copy(canDelete = true))
        assertEquals(71L, vm.state.value.confirmDelete?.postId)
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
    fun `알림으로 들어오면 게시판은 고른 칩부터 읽는다(CREW_NOTICE, CREW_POLL, D-150)`() {
        val vm = CrewBoardViewModel(repo, MutableClock()).also { it.bind(11, BoardFilter.POLL) }
        assertEquals(BoardFilter.POLL, vm.state.value.filter)
        assertTrue(repo.calls.any { it.startsWith("posts:POLL") })
        vm.bind(11, BoardFilter.NOTICE)
        assertEquals(BoardFilter.POLL, vm.state.value.filter, "이미 연 게시판은 칩을 그대로 둔다")
    }

    @Test
    fun `고정이 3개면 PIN_LIMIT를 알린다`() {
        repo.role = CrewRole.MANAGER
        repo.fail["pin"] = AppError.Rejected("PIN_LIMIT")
        val vm = CrewBoardViewModel(repo, MutableClock()).also { it.bind(11, BoardFilter.NOTICE) }
        vm.onPinToggle(notices().posts[2])
        assertTrue("pin:53:true" in repo.calls)
        assertEquals(UiText.Res(R.string.crew_error_pin_limit), vm.state.value.message)
    }

    @Test
    fun `운영진 이상은 남의 공지도 확인 뒤 지운다(FR-213)`() {
        repo.role = CrewRole.MANAGER
        val vm = CrewBoardViewModel(repo, MutableClock()).also { it.bind(11, BoardFilter.NOTICE) }
        vm.onDeleteAsk(notices().posts[0].copy(canDelete = true))
        vm.onDeleteConfirm()
        assertTrue("deletePost:51" in repo.calls)
    }

    @Test
    fun `공지 글자 수는 코드 포인트로 센다 - 이모지 1000자는 받고 넘치면 서로게이트를 가르지 않고 자른다(D-143)`() {
        val vm = CrewNoticeWriteViewModel(repo).also { it.bind(11) }
        vm.onBody(EMOJI.repeat(1_000))
        assertTrue(vm.state.value.canSubmit)
        vm.onBody(EMOJI.repeat(1_001) + "가")
        assertEquals(EMOJI.repeat(1_000), vm.state.value.body)
    }

    @Test
    fun `마감된 투표 응답은 POLL_CLOSED를 알리고 결과를 다시 읽는다`() {
        repo.fail["respond"] = AppError.Rejected("POLL_CLOSED")
        val vm = CrewPollDetailViewModel(repo, MutableClock()).also { it.bind(11, 61) }
        vm.onRespond(2)
        assertEquals(UiText.Res(R.string.crew_error_poll_closed), vm.state.value.message)
        assertEquals(2, repo.calls.count { it == "pollResult:61" })
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

/** 투표 만들기(FR-206, D-155, D-165): 질문 1~200, 선택지 2~5개·각 1~40, 찬반은 선택지 없이, 마감은 지금 뒤. */
class CrewPollWriteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    @Test
    fun `선택지는 2~5개이고 빈 선택지가 있으면 올리지 않는다`() {
        val vm = CrewPollWriteViewModel(repo, clock)
        vm.onQuestion("대회 어디?")
        assertFalse(vm.state.value.isValid, "선택지 2개가 비었다")
        vm.onOption(0, "춘천")
        vm.onOption(1, "JTBC")
        assertTrue(vm.state.value.isValid)
        repeat(5) { vm.onAddOption() }
        assertEquals(5, vm.state.value.options.size)
        assertFalse(vm.state.value.isValid, "빈 선택지 3개")
        vm.onRemoveOption(4)
        vm.onRemoveOption(3)
        vm.onRemoveOption(2)
        vm.onRemoveOption(1)
        assertEquals(2, vm.state.value.options.size, "2개 밑으로 줄이지 않는다")
        vm.onOption(0, "가".repeat(41))
        assertEquals(40, vm.state.value.options[0].length)
    }

    @Test
    fun `투표 질문 200자·선택지 40자는 코드 포인트로 센다(D-143)`() {
        val vm = CrewPollWriteViewModel(repo, clock)
        vm.onQuestion(EMOJI.repeat(201))
        vm.onOption(0, EMOJI.repeat(41))
        vm.onOption(1, EMOJI.repeat(40))

        assertEquals(EMOJI.repeat(200), vm.state.value.question)
        assertEquals(EMOJI.repeat(40), vm.state.value.options[0])
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `찬반은 선택지 없이 보내고 지난 마감은 거부한다`() {
        val vm = CrewPollWriteViewModel(repo, clock)
        vm.onQuestion(" 회식 할까요? ")
        vm.onMethod(PollMethod.YES_NO)
        assertTrue(vm.state.value.isValid)
        vm.onDay(0)
        vm.onTime(LocalTime.of(6, 0))
        vm.onSubmit(11)
        assertEquals(UiText.Res(R.string.crew_poll_write_past_time), vm.state.value.error)
        vm.onDay(1)
        vm.onTime(LocalTime.of(21, 0))
        vm.onSubmit(11)
        assertTrue("writePoll:회식 할까요?:YES_NO:[]" in repo.calls)
        assertEquals(Instant.parse("2026-10-21T12:00:00Z"), repo.lastPollClosesAt, "10/21(수) 21:00 KST")
        assertTrue(vm.state.value.isPosted)
    }
}

/** 게시판(와이어프레임 E3·M6, FR-205·FR-206·FR-129·FR-211·FR-212): 종류 칩, 고정 공지, 응원, 고정, 앨범. */
class CrewBoardViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm(filter: BoardFilter = BoardFilter.POST) = CrewBoardViewModel(repo, MutableClock()).also {
        it.bind(11)
        it.onFilter(filter)
    }

    @Test
    fun `전체 칩은 일정 글을 빼고 고정 공지를 맨 위에 둔다, 인원과 역할을 읽는다`() {
        repo.role = CrewRole.MANAGER
        val vm = CrewBoardViewModel(repo, MutableClock()).also { it.bind(11) }
        val s = vm.state.value
        assertTrue("posts:null" in repo.calls)
        assertTrue(s.posts!!.posts.none { it.kind == PostList.Kind.CREW_RUN })
        assertEquals(listOf(51L, 52L, 53L, 61L), s.shownPosts?.take(4)?.map { it.postId })
        assertEquals(1, s.shownPosts?.count { it.postId == 51L })
        assertEquals(6, s.headcount)
        assertTrue(s.canManage)
    }

    @Test
    fun `칩을 바꾸면 그 종류를 읽고 앨범은 고를 때 읽는다`() {
        val vm = vm()
        assertEquals(2, vm.state.value.posts?.posts?.size)
        assertTrue(repo.calls.none { it.startsWith("album") })
        vm.onFilter(BoardFilter.ALBUM)
        assertEquals(listOf(901L, 902L), vm.state.value.album?.photos?.map { it.photoId })
        vm.onFilter(BoardFilter.POLL)
        assertTrue("posts:POLL" in repo.calls)
        assertEquals(listOf(61L, 62L), vm.state.value.shownPosts?.map { it.postId })
    }

    @Test
    fun `응원 칩에서 응원을 남기면 칸을 비우고 다시 읽는다(FR-129)`() {
        val vm = vm(BoardFilter.CHEER)
        vm.onCheerDraft("  ")
        assertFalse(vm.state.value.isCheerValid)
        vm.onCheerDraft("같이 달려요")
        vm.onCheerSubmit()
        assertTrue("cheer:같이 달려요" in repo.calls)
        assertEquals("", vm.state.value.cheerDraft)
        assertEquals(2, repo.calls.count { it == "posts:CHEER" })
    }

    @Test
    fun `운영진만 공지를 고정하거나 푼다(FR-205)`() {
        val member = vm(BoardFilter.NOTICE)
        member.onPinToggle(notices().posts[2])
        assertTrue(repo.calls.none { it.startsWith("pin:") })
        repo.role = CrewRole.LEADER
        val leader = vm(BoardFilter.NOTICE)
        leader.onPinToggle(notices().posts[2])
        leader.onPinToggle(notices().posts[0])
        assertEquals(listOf("pin:53:true", "pin:51:false"), repo.calls.filter { it.startsWith("pin:") })
    }

    @Test
    fun `사진을 누르면 사진 보기를 열고 닫는다`() {
        val vm = vm()
        vm.onOpenPhoto(902)
        assertEquals(902L, vm.state.value.viewer)
        vm.onClosePhoto()
        assertNull(vm.state.value.viewer)
    }

    @Test
    fun `사진은 ID로 받는다(D-100)`() = main.runVmTest {
        val vm = vm()
        assertEquals(listOf<Byte>(1, 2, 3), (vm.photoBytes(901) as Outcome.Success).value.toList())
        assertTrue("photo:901" in repo.calls)
    }

    @Test
    fun `갱신 이어 읽기는 서버가 같은 커서를 되풀이해도 멈춘다(A4 리뷰)`() = main.runVmTest {
        repo.postsNext = 99
        repo.cursorPageNext = 99
        val r = repo.postsCovering(11, PostList.Kind.BOARD, loaded = 100)
        assertTrue(r is Outcome.Success)
        assertEquals(1, repo.calls.count { it == "posts:BOARD:99" })
    }

    @Test
    fun `삭제 뒤 다시 읽어도 이어 받은 뒤쪽 글이 남는다(A4)`() {
        repo.postsNext = 99
        val vm = vm()
        vm.onLoadMore()
        vm.onDeleteAsk(boards.posts[1])
        vm.onDeleteConfirm()
        assertTrue(vm.state.value.posts!!.posts.any { it.postId == 49L })
    }

    @Test
    fun `지울 수 있는 글만 확인 뒤 지우고 다시 읽는다`() {
        val vm = vm()
        vm.onDeleteAsk(boards.posts[0])
        assertNull(vm.state.value.confirmDelete)
        vm.onDeleteAsk(boards.posts[1])
        vm.onDeleteConfirm()
        assertTrue("deletePost:82" in repo.calls)
        assertEquals(2, repo.calls.count { it == "posts:BOARD" })
    }
}

/** 게시판 글쓰기(FR-211, D-100): 사진 0~5장, 압축 거부 안내, 본문·사진 중 하나는 필요. */
class CrewBoardWriteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `사진만으로도 올리고 5장을 넘기면 자르고 알린다(TC-7439)`() {
        val vm = CrewBoardWriteViewModel(repo)
        assertFalse(vm.state.value.canSubmit)
        vm.onPhotosPicked((1..4).map { "content://p/$it" }, rejectedCount = 0)
        vm.onPhotosPicked(listOf("content://p/5", "content://p/6"), rejectedCount = 0)
        assertEquals(5, vm.state.value.photos.size)
        assertEquals(UiText.Res(R.string.crew_board_photo_limit), vm.state.value.message)
        assertTrue(vm.state.value.canSubmit)
        vm.onRemovePhoto("content://p/5")
        vm.onSubmit(11)
        assertTrue("writeBoard::[content://p/1, content://p/2, content://p/3, content://p/4]" in repo.calls)
        assertTrue(vm.state.value.isPosted)
    }

    @Test
    fun `고를 때 걸러진 사진(10MB 초과·JPEG·PNG 아님)은 안내한다(TC-7440·TC-7441)`() {
        val vm = CrewBoardWriteViewModel(repo)
        vm.onPhotosPicked(listOf("content://p/1"), rejectedCount = 2)
        assertEquals(listOf("content://p/1"), vm.state.value.photos)
        assertEquals(UiText.Res(R.string.crew_board_photo_rejected), vm.state.value.message)
    }

    @Test
    fun `줄이다 거부되면(TOO_LARGE·UNSUPPORTED_IMAGE) 그 이유를 보이고 머문다`() {
        repo.fail["writeBoard"] = AppError.Rejected("UNSUPPORTED_IMAGE")
        val vm = CrewBoardWriteViewModel(repo)
        vm.onBody("노을")
        vm.onPhotosPicked(listOf("content://p/1"), rejectedCount = 0)
        vm.onSubmit(11)
        assertEquals(UiText.Res(R.string.crew_error_unsupported_image), vm.state.value.error)
        assertFalse(vm.state.value.isPosted)
    }

    @Test
    fun `게시글 글자 수는 코드 포인트로 센다 - 이모지 1000자는 올린다(D-143)`() {
        val vm = CrewBoardWriteViewModel(repo)
        vm.onBody(EMOJI.repeat(1_001))
        assertEquals(EMOJI.repeat(1_000), vm.state.value.body)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `본문은 1000자까지, 공백뿐이고 사진도 없으면 올리지 않는다(D-165)`() {
        val vm = CrewBoardWriteViewModel(repo)
        vm.onBody("   ")
        assertFalse(vm.state.value.canSubmit)
        vm.onBody("가".repeat(1_001))
        assertEquals(1_000, vm.state.value.body.length)
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

/** 운영진 지정·해제(FR-204, TC-7351~TC-7355)와 운영진의 관리 화면. */
class CrewManageV011ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm(role: CrewRole) = CrewManageViewModel(repo).also {
        repo.role = role
        it.bind(Routes.CrewManage(11))
    }

    @Test
    fun `크루장이 크루원을 운영진으로 지정하고 운영진을 해제한다`() {
        val vm = vm(CrewRole.LEADER)
        assertTrue(vm.state.value.isLeader)
        vm.onAsk(ManageAction.Appoint(members.members[1], 0))
        vm.onConfirm()
        assertTrue("appoint:2" in repo.calls)
        assertEquals(UiText.Res(R.string.crew_manage_appointed, listOf("달빛")), vm.state.value.message)
        vm.onDismissManager(members.members[1].copy(role = CrewRole.MANAGER))
        assertTrue("dismiss:2" in repo.calls)
    }

    @Test
    fun `운영진이 2명이면 MANAGER_LIMIT를 알린다`() {
        repo.fail["appoint"] = AppError.Rejected("MANAGER_LIMIT")
        val vm = vm(CrewRole.LEADER)
        vm.onAsk(ManageAction.Appoint(members.members[1], 2))
        vm.onConfirm()
        assertEquals(UiText.Res(R.string.crew_error_manager_limit), vm.state.value.message)
    }

    @Test
    fun `운영진은 크루장 기능을 보지 못한다`() {
        val vm = vm(CrewRole.MANAGER)
        assertFalse(vm.state.value.isLeader)
    }
}

/** 일정 쓰기: 대면 모임(FR-132, D-151, D-155): 장소 1~50자, 정원 2~10(비우면 크루 정원), 지난 일시는 거부. */
class CrewMeetupWriteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    @Test
    fun `대면 모임은 내용·장소가 있어야 올리고 정원을 고를 수 있다`() {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onKind(PostList.Kind.MEETUP)
        vm.onBody("번개 후 맥주")
        assertFalse(vm.state.value.isValid, "장소가 없다")
        vm.onPlace("가".repeat(60))
        assertEquals(50, vm.state.value.placeName.length)
        vm.onPlace(" 여의나루역 ")
        vm.onCapacity(8)
        vm.onDay(2)
        vm.onTime(LocalTime.of(19, 0))
        vm.onSubmit(11)
        assertTrue("writeMeetup:번개 후 맥주:여의나루역:8" in repo.calls)
        assertEquals(Instant.parse("2026-10-22T10:00:00Z"), repo.lastMeetupAt)
        assertTrue(repo.calls.none { it.startsWith("writeCrewRun") })
    }

    @Test
    fun `장소명 50자는 코드 포인트로 센다(D-143)`() {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onKind(PostList.Kind.MEETUP)
        vm.onPlace(EMOJI.repeat(51))
        assertEquals(EMOJI.repeat(50), vm.state.value.placeName)
    }

    @Test
    fun `일정 내용 글자 수는 코드 포인트로 센다 - 이모지는 반으로 자르지 않는다(D-143)`() {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onBody(EMOJI.repeat(201))
        assertEquals(EMOJI.repeat(200), vm.state.value.body)
    }

    @Test
    fun `정원은 2~10명이고 비우면 크루 정원이다`() {
        val vm = CrewRunWriteViewModel(repo, clock)
        vm.onKind(PostList.Kind.MEETUP)
        vm.onCapacity(1)
        assertNull(vm.state.value.capacity)
        vm.onCapacity(11)
        assertNull(vm.state.value.capacity)
        vm.onCapacity(10)
        assertEquals(10, vm.state.value.capacity)
        vm.onCapacity(null)
        assertNull(vm.state.value.capacity)
    }
}

/** UTF-16 두 칸을 쓰는 글자 하나(코드 포인트 1개, D-143). */
private const val EMOJI = "\uD83C\uDFC3"
