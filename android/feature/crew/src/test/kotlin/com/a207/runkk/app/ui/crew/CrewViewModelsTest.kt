@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.MyCrew
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CrewFindViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `크루가 없으면 내 크루를 확인한 뒤 공개 크루 목록을 읽고 추천은 읽지 않는다(FR-117, FR-118 삭제)`() {
        val vm = CrewFindViewModel(repo)
        val s = vm.state.value
        assertEquals("myCrew", repo.calls.first())
        assertFalse(s.isCheckingMine)
        assertNull(s.mine)
        assertEquals(3, s.crews.size)
        assertTrue("search:null:[]" in repo.calls)
    }

    @Test
    fun `크루 탭에 다시 들어왔을 때 소속 크루가 있으면 찾기 목록을 읽지 않고 내 크루를 보인다`() {
        repo.mine = MyCrew(crewDetail(11), emptyList())
        val vm = CrewFindViewModel(repo)
        assertEquals(11L, vm.state.value.mine?.crewId)
        assertTrue(repo.calls.none { it.startsWith("search") })
    }

    @Test
    fun `만들기 화면에서 돌아오면 소속을 다시 확인해 방금 만든 크루 홈을 보인다(E2E에서 찾은 결함)`() {
        val vm = CrewFindViewModel(repo)
        assertNull(vm.state.value.mine)

        // 크루 만들기 뒤 크루 탭(같은 항목·같은 ViewModel)으로 돌아온다.
        repo.mine = MyCrew(crewDetail(77), emptyList())
        vm.refreshMine()

        assertEquals(77L, vm.state.value.mine?.crewId)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `N-12 공개 크루가 0개여도 돌아올 때마다 찾기 목록을 다시 읽지 않는다`() {
        repo.searchFirstPage = emptyList()
        val vm = CrewFindViewModel(repo)
        assertEquals(1, repo.calls.count { it.startsWith("search") })

        vm.refreshMine()
        vm.refreshMine()

        assertEquals(1, repo.calls.count { it.startsWith("search") })
    }

    @Test
    fun `이미 내 크루를 보이고 있으면 돌아와도 다시 확인하지 않는다`() {
        repo.mine = MyCrew(crewDetail(11), emptyList())
        val vm = CrewFindViewModel(repo)
        val before = repo.calls.count { it == "myCrew" }

        vm.refreshMine()

        assertEquals(before, repo.calls.count { it == "myCrew" })
    }

    @Test
    fun `내 크루를 확인하지 못하면 찾기를 보이지 않고 다시 시도하게 한다`() {
        repo.fail["myCrew"] = AppError.Network
        val vm = CrewFindViewModel(repo)
        assertNotNull(vm.state.value.mineError)
        assertTrue(repo.calls.none { it.startsWith("search") })
        repo.fail.remove("myCrew")
        vm.loadMine()
        assertNull(vm.state.value.mineError)
        assertEquals(3, vm.state.value.crews.size)
    }

    @Test
    fun `대기 중인 내 가입 신청을 보이고 확인 뒤 취소한다(FR-143)`() {
        val request = MyCrew.PendingJoin(91, 12, "저녁런 클럽", start)
        repo.mine = MyCrew(null, listOf(request))
        val vm = CrewFindViewModel(repo)
        assertEquals(listOf(request), vm.state.value.pending)
        vm.onCancelAsk(request)
        assertEquals(request, vm.state.value.confirmCancel)
        assertTrue(repo.calls.none { it.startsWith("cancelJoin") }, "확인 전에는 보내지 않는다")
        vm.onCancelConfirm()
        assertTrue("cancelJoin:91" in repo.calls)
        assertEquals(emptyList(), vm.state.value.pending)
        assertEquals(uiText(R.string.crew_find_cancelled_message, "저녁런 클럽"), vm.state.value.message)
    }

    @Test
    fun `가입 신청 취소가 거부되면 사유를 보이고 소속·신청을 다시 읽는다`() {
        val request = MyCrew.PendingJoin(91, 12, "저녁런 클럽", start)
        repo.mine = MyCrew(null, listOf(request))
        repo.fail["cancelJoin"] = AppError.Rejected("REQUEST_DECIDED")
        val vm = CrewFindViewModel(repo)
        vm.onCancelAsk(request)
        vm.onCancelConfirm()
        assertEquals(UiText.Res(R.string.crew_error_request_decided), vm.state.value.message)
        assertEquals(2, repo.calls.count { it == "myCrew" })
    }

    @Test
    fun `목록 끝에 닿으면 다음 쪽을 이어 받고 끝이면 더 부르지 않는다`() {
        repo.searchNext = 40
        val vm = CrewFindViewModel(repo)
        vm.loadMore()
        assertEquals(listOf(11L, 12L, 13L, 14L), vm.state.value.crews.map { it.crewId })
        assertNull(vm.state.value.nextCursor)
        vm.loadMore()
        assertEquals(1, repo.calls.count { it == "search:null:[]:40" })
    }

    @Test
    fun `조건 팝업에서 고른 태그는 적용하기를 눌러야 찾기에 쓰이고 키워드는 검색할 때 다시 찾는다(F-01b)`() {
        val vm = CrewFindViewModel(repo)
        vm.onConditionOpen()
        vm.onConditionToggle("WEEKDAY_EVENING")
        vm.onConditionToggle("WEEKEND_MORNING")
        assertEquals(emptySet(), vm.state.value.tags, "적용 전에는 찾기 조건이 그대로다")
        vm.onConditionApply()
        assertEquals(setOf("WEEKDAY_EVENING", "WEEKEND_MORNING"), vm.state.value.tags)
        assertEquals(false, vm.state.value.isConditionOpen)
        vm.onKeyword("  새벽 ")
        vm.load()
        assertEquals("search:새벽:[WEEKDAY_EVENING, WEEKEND_MORNING]", repo.calls.last { it.startsWith("search") })
    }

    @Test
    fun `승인제면 신청 안내와 함께 대기 중 신청에 더하고, 가입되면 그 자리에서 내 크루 홈(FR-120·FR-121)`() {
        val vm = CrewFindViewModel(repo)
        vm.onJoin(crewC)
        assertEquals(UiText.Res(R.string.crew_find_applied_message), vm.state.value.message)
        assertEquals(listOf(91L), vm.state.value.pending.map { it.requestId })
        assertNull(vm.state.value.mine)
        assertEquals(JoinPolicy.APPROVAL, crewC.joinPolicy)
        vm.onJoin(crewA)
        assertEquals(11L, vm.state.value.mine?.crewId)
    }

    @Test
    fun `가입이 거부되면 사유를 보인다(정원·쿨타임)`() {
        repo.fail["apply"] = AppError.Rejected("COOLDOWN")
        val vm = CrewFindViewModel(repo)
        vm.onOpen(crewC)
        vm.onJoin(crewC)
        assertEquals(UiText.Res(R.string.crew_error_cooldown), vm.state.value.message)
        assertFalse(vm.state.value.isJoining)
    }

    @Test
    fun `초대 코드는 6자리 영문·숫자 대문자로 받고 가입한다(FR-122)`() {
        val vm = CrewFindViewModel(repo)
        vm.onInviteOpen()
        vm.onInviteCode("ab-12cd99")
        assertEquals("AB12CD", vm.state.value.inviteCode)
        vm.onInviteSubmit()
        assertTrue("invite:AB12CD" in repo.calls)
        assertFalse(vm.state.value.isInviteOpen)
        assertEquals(12L, vm.state.value.mine?.crewId, "초대 코드로 가입한 크루 홈을 연다")
    }

    @Test
    fun `초대 코드를 너무 많이 틀리면(429 TOO_MANY_ATTEMPTS) 기다리라고 안내한다`() {
        repo.fail["invite"] = AppError.Rejected("TOO_MANY_ATTEMPTS")
        val vm = CrewFindViewModel(repo)
        vm.onInviteOpen()
        vm.onInviteCode("AB12CD")
        vm.onInviteSubmit()
        assertEquals(UiText.Res(R.string.crew_error_too_many_attempts), vm.state.value.message)
    }
}

class CrewCreateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `이름 2~20자·태그 1~5개가 아니면 보내지 않는다`() {
        val vm = CrewCreateViewModel(repo)
        vm.onForm(CrewFormState(name = "a", tags = setOf("WEEKDAY_EVENING")))
        vm.onSubmit()
        vm.onForm(CrewFormState(name = "새벽질주"))
        vm.onSubmit()
        assertTrue(repo.calls.none { it.startsWith("create") })
    }

    @Test
    fun `만들면 크루 탭(내 크루 홈)으로(FR-110)`() = runTest(main.dispatcher) {
        val vm = CrewCreateViewModel(repo)
        vm.onForm(CrewFormState(name = " 새벽질주 ", tags = setOf("WEEKDAY_EVENING", "WEEKEND_MORNING")))
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewEvent.Created, awaitItem())
        }
        assertTrue("create:새벽질주" in repo.calls)
    }

    @Test
    fun `크루명이 겹치면 이름 중복 문구를 보인다`() {
        repo.fail["create"] = AppError.Rejected("CREW_NAME_TAKEN")
        val vm = CrewCreateViewModel(repo)
        vm.onForm(CrewFormState(name = "새벽질주", tags = setOf("WEEKDAY_EVENING")))
        vm.onSubmit()
        assertEquals(UiText.Res(R.string.crew_error_crew_name_taken), vm.state.value.error)
    }
}

class CrewHomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()
    private val serverClock = ServerClock(clock)

    private fun vm() = CrewHomeViewModel(repo, serverClock, FakeCelebrationSharer()).also {
        it.bind(Routes.CrewHome(11))
    }

    /** 모집글 31(투표 중) 모집 일시에서 [minutes]분 앞. */
    private fun beforeMeet(minutes: Long): Instant = crewRunVoting.scheduledAt.minusSeconds(minutes * 60)

    @Test
    fun `크루런 대기실 단추 - 열리기 전엔 남은 분, 30분 전에 저절로 열리고, 모집 일시가 지나면 닫힌다(FR-133·FR-147)`() = main.runVmTest { vms ->
        clock.now = beforeMeet(40)
        val vm = vms.track(vm())
        vm.onTab(CrewTab.SCHEDULE)
        assertEquals(LobbyAvailability.NotYet(10), vm.state.value.lobbyEntries[31])
        assertEquals(LobbyAvailability.Closed, vm.state.value.lobbyEntries[32], "끝난 크루런")
        clock.now = beforeMeet(30)
        advanceTimeBy(1_100)
        assertEquals(LobbyAvailability.Open, vm.state.value.lobbyEntries[31], "다시 읽지 않아도 시각이 되면 연다")
        clock.now = beforeMeet(0)
        advanceTimeBy(1_100)
        assertEquals(LobbyAvailability.Closed, vm.state.value.lobbyEntries[31])
    }

    @Test
    fun `크루런 강조 카드의 지금 시각은 서버 시각으로 1초마다 갱신한다(F-11)`() = main.runVmTest { vms ->
        clock.now = beforeMeet(40)
        val vm = vms.track(vm())
        vm.onTab(CrewTab.SCHEDULE)
        assertEquals(beforeMeet(40), vm.state.value.scheduleNow)
        clock.now = clock.now.plusSeconds(1)
        advanceTimeBy(1_100)
        assertEquals(beforeMeet(40).plusSeconds(1), vm.state.value.scheduleNow)
    }

    @Test
    fun `참석 칩을 끄면 응답을 거두고 목록을 다시 읽는다(W6 참석 취소)`() = main.runVmTest { vms ->
        clock.now = beforeMeet(40)
        val vm = vms.track(vm())
        vm.onTab(CrewTab.SCHEDULE)
        val post = vm.state.value.crewRuns!!.posts.first { it.postId == 31L }
        vm.onAttendance(post, AttendanceAnswer.GOING)
        runCurrent()
        assertEquals(
            AttendanceAnswer.GOING,
            vm.state.value.crewRuns!!.posts.first {
                it.postId == 31L
            }.crewRun?.myAnswer
        )
        vm.onAttendanceWithdraw(post)
        runCurrent()
        assertTrue("withdraw:31" in repo.calls)
        assertNull(vm.state.value.crewRuns!!.posts.first { it.postId == 31L }.crewRun?.myAnswer, "응답 뒤 다시 읽은 목록의 값")
    }

    @Test
    fun `크루런 대기실 단추는 기기 시계가 아니라 서버 시각으로 판단한다`() = main.runVmTest { vms ->
        // 기기 시계가 2분 빠르다: 기기로는 29분 전이지만 서버로는 31분 전이라 아직 안 열렸다.
        clock.now = beforeMeet(29)
        serverClock.onServerDate(beforeMeet(31))
        val vm = vms.track(vm())
        vm.onTab(CrewTab.SCHEDULE)
        assertEquals(LobbyAvailability.NotYet(1), vm.state.value.lobbyEntries[31])
    }

    @Test
    fun `크루 이름·인원·역할은 크루 조회로 읽고 크루 관리는 크루장·운영진에게만 연다`() {
        val member = vm()
        assertEquals("새벽질주", member.state.value.crewName)
        assertEquals(6, member.state.value.detail?.headcount)
        assertFalse(member.state.value.canManage)
        repo.role = CrewRole.LEADER
        val leader = CrewHomeViewModel(repo, serverClock, FakeCelebrationSharer()).also { it.bind(Routes.CrewHome(11)) }
        assertTrue(leader.state.value.canManage)
    }

    @Test
    fun `레이스 탭 - 이번 주 거리·기여도와 주간 랭킹, 기간을 바꾸면 다시 읽는다(FR-137·FR-140)`() {
        val vm = vm()
        assertEquals(race, vm.state.value.race)
        assertEquals(3, vm.state.value.ranking?.entries?.size)
        vm.onPeriod(CrewRanking.Period.MONTHLY)
        assertTrue("ranking:MONTHLY" in repo.calls)
        assertEquals(22, shareLabel(0.22))
        assertEquals(22, shareLabel(22.7))
    }

    @Test
    fun `탭과 크루원 쪽은 처음 열 때 한 번만 읽는다`() {
        val vm = vm()
        vm.onPage(CrewHomePage.MEMBERS)
        vm.onPage(CrewHomePage.HOME)
        vm.onPage(CrewHomePage.MEMBERS)
        assertEquals(1, repo.calls.count { it == "members" }, repo.calls.toString())
        vm.onPage(CrewHomePage.HOME)
        vm.onTab(CrewTab.SCHEDULE)
        vm.onTab(CrewTab.RACE)
        vm.onTab(CrewTab.SCHEDULE)
        assertEquals(1, repo.calls.count { it == "posts:CREW_RUN" }, repo.calls.toString())
    }

    @Test
    fun `지울 수 있는 일정 글만, 확인을 받은 뒤 지운다(FR-130)`() {
        val vm = vm()
        vm.onTab(CrewTab.SCHEDULE)
        vm.onDeleteAsk(crewRunPosts.posts[0].copy(canDelete = false))
        assertNull(vm.state.value.confirmDelete, "남의 글은 묻지도 않는다")
        vm.onDeleteAsk(crewRunPosts.posts[0].copy(canDelete = true))
        assertTrue(repo.calls.none { it.startsWith("deletePost") })
        vm.onDeleteConfirm()
        assertEquals(listOf("deletePost:31"), repo.calls.filter { it.startsWith("deletePost") })
    }

    @Test
    fun `크루런 참여 응답을 남기면 그 글의 내 응답으로 보이고 서버 목록을 다시 읽는다(FR-145)`() {
        val vm = vm()
        vm.onTab(CrewTab.SCHEDULE)
        assertNull(vm.state.value.crewRuns?.posts?.first()?.crewRun?.myAnswer)
        vm.onAttendance(crewRunPosts.posts[0], AttendanceAnswer.GOING)
        assertTrue("vote:31:GOING" in repo.calls)
        assertEquals(2, repo.calls.count { it == "posts:CREW_RUN" }, "투표 뒤 참여 인원·내 응답을 서버에서 다시 읽는다")
    }

    @Test
    fun `확인 뒤 크루를 나가면 크루 탭으로(FR-125)`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.onLeaveAsk()
        assertTrue(vm.state.value.isConfirmingLeave)
        vm.events.test {
            vm.onLeaveConfirm()
            assertEquals(CrewHomeEvent.Left, awaitItem())
        }
    }

    @Test
    fun `크루장이 크루원이 남은 채 나가려 하면 양도를 안내한다(FR-126)`() {
        repo.fail["leave"] = AppError.Rejected("LEADER_MUST_TRANSFER")
        val vm = vm()
        vm.onLeaveConfirm()
        assertEquals(UiText.Res(R.string.crew_error_leader_must_transfer), vm.state.value.message)
    }

    @Test
    fun `크루 내 소개는 40자까지 바꾼다(FR-144)`() {
        val vm = vm()
        vm.onIntroEdit()
        vm.onIntroDraft("가".repeat(50))
        assertEquals(40, vm.state.value.introDraft.length)
        vm.onIntroDraft("새벽 5시 고정")
        vm.onIntroSave()
        assertTrue("intro:새벽 5시 고정" in repo.calls)
    }
}

class CrewManageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm() = CrewManageViewModel(repo).also { it.bind(Routes.CrewManage(11)) }

    @Test
    fun `정보 수정 칸과 초대 코드는 크루 조회로 채운다(FR-112·FR-113)`() {
        repo.role = CrewRole.LEADER
        val vm = vm()
        val form = vm.state.value.form
        assertEquals("새벽질주", vm.state.value.crewName)
        assertEquals("새벽에 같이 달려요", form.intro)
        assertEquals(setOf("WEEKDAY_EVENING", "WEEKDAY_DAWN"), form.tags)
        assertEquals(JoinPolicy.FREE, form.joinPolicy)
        assertEquals("K7P2QX", vm.state.value.inviteCode)
        assertTrue("detail:11" in repo.calls)
    }

    @Test
    fun `가입 신청을 승인하면 안내하고 다시 읽는다(FR-123·FR-136)`() {
        val vm = vm()
        val request = vm.state.value.requests!!.requests.first()
        vm.onApprove(request)
        assertEquals(uiText(R.string.crew_manage_approved, "새내기"), vm.state.value.message)
        assertEquals(2, repo.calls.count { it == "pending" })
    }

    @Test
    fun `초대 코드를 새로 만든다(FR-114)`() {
        val vm = vm()
        vm.onReissue()
        assertEquals("K7P2QX", vm.state.value.inviteCode)
    }

    @Test
    fun `내보내기·크루장 넘기기는 확인 뒤에 보낸다(FR-115·FR-127)`() {
        val vm = vm()
        val member = members.members[1]
        vm.onAsk(ManageAction.Kick(member))
        assertTrue(repo.calls.none { it.startsWith("kick") })
        vm.onConfirm()
        assertTrue("kick:2" in repo.calls)
        vm.onAsk(ManageAction.Transfer(member))
        vm.onDismiss()
        assertTrue(repo.calls.none { it.startsWith("transfer") })
    }

    @Test
    fun `크루장이 아니면 서버 거부를 안내한다(FR-128)`() {
        repo.fail["reissue"] = AppError.Rejected("PERMISSION_DENIED")
        val vm = vm()
        vm.onReissue()
        assertEquals(UiText.Res(R.string.crew_error_permission_denied), vm.state.value.message)
    }

    @Test
    fun `크루를 지우면 크루 탭으로(FR-116)`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.events.test {
            vm.onAsk(ManageAction.Delete)
            vm.onConfirm()
            assertEquals(CrewManageEvent.Deleted, awaitItem())
        }
    }

    @Test
    fun `정보 수정이 거부되면 제목을 바꾸지 않는다`() {
        repo.fail["changeInfo"] = AppError.Rejected("CREW_NAME_INVALID")
        val vm = vm()
        vm.onForm(vm.state.value.form.copy(name = "겹치는이름", tags = setOf("WEEKDAY_DAWN")))
        vm.onSaveInfo()
        assertEquals("새벽질주", vm.state.value.crewName)
        assertEquals(UiText.Res(R.string.crew_error_crew_name_invalid), vm.state.value.message)
    }

    @Test
    fun `정보 수정은 이름을 채워 두고 저장한다(FR-113)`() {
        val vm = vm()
        assertEquals("새벽질주", vm.state.value.form.name)
        vm.onForm(vm.state.value.form.copy(name = "새벽질주2", tags = setOf("WEEKDAY_DAWN")))
        vm.onSaveInfo()
        assertTrue("changeInfo:새벽질주2" in repo.calls)
        assertEquals("새벽질주2", vm.state.value.crewName)
    }
}

class CrewRunWriteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    /** 2026-10-20(화) 19:00 KST. */
    private val clock = MutableClock()

    private fun created() = CrewRunWriteViewModel(repo, clock, seoul).also { it.bind(CrewRunWriteMode.Create(11)) }

    @Test
    fun `모집하기 - 제목·날짜·시간·거리를 다 채워야 올리고, 지난 시각은 거부한다(F-12, FR-132)`() = runTest(main.dispatcher) {
        val vm = created()
        val today = vm.state.value.today
        assertEquals(LocalDate.of(2026, 10, 20), today)
        vm.onTitle("토요일 한강")
        assertFalse(vm.state.value.isValid, "날짜·시간·거리 자리 문구 상태")
        vm.onDate(today)
        vm.onTime(LocalTime.of(7, 0))
        vm.onDistance(CrewRunDistanceChoice.KM10)
        vm.onSubmit()
        assertEquals(UiText.Res(R.string.crew_run_write_past_time), vm.state.value.error)
        vm.onDate(LocalDate.of(2026, 10, 24))
        vm.onRepeat(CrewRunRepeat.WEEKLY)
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        val form = assertNotNull(repo.lastCrewRun)
        assertEquals("토요일 한강", form.title)
        assertNull(form.body, "빈 내용은 보내지 않는다")
        assertEquals(Distance(10_000), form.distance)
        assertEquals(CrewRunRepeat.WEEKLY, form.repeat)
        assertEquals(java.time.Instant.parse("2026-10-23T22:00:00Z"), form.meetAt, "10/24(토) 07:00 KST")
    }

    @Test
    fun `날짜는 오늘~14일 뒤, 시간은 5분 단위만 받고 제한 없음 거리는 null로 보낸다(F-12b·d·e)`() = runTest(main.dispatcher) {
        val vm = created()
        val today = vm.state.value.today
        vm.onDate(today.plusDays(15))
        assertNull(vm.state.value.date)
        vm.onDate(today.minusDays(1))
        assertNull(vm.state.value.date)
        vm.onDate(today.plusDays(14))
        assertEquals(today.plusDays(14), vm.state.value.date)
        vm.onTime(LocalTime.of(7, 3))
        assertNull(vm.state.value.time)
        vm.onTime(LocalTime.of(6, 55))
        vm.onDistance(CrewRunDistanceChoice.NO_LIMIT)
        vm.onTitle("멀리")
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        assertNull(assertNotNull(repo.lastCrewRun).distance)
    }

    @Test
    fun `앱 시계가 UTC여도 날짜와 모집 일시는 기기 시간대로 셈한다(MR !91 e2e 2 후속)`() = runTest(main.dispatcher) {
        // 한국 10/21(수) 00:30 = UTC 10/20 15:30
        val utc = MutableClock(java.time.Instant.parse("2026-10-20T15:30:00Z"), java.time.ZoneOffset.UTC)
        val vm = CrewRunWriteViewModel(repo, utc, seoul).also { it.bind(CrewRunWriteMode.Create(11)) }
        assertEquals(LocalDate.of(2026, 10, 21), vm.state.value.today)
        vm.onTitle("저녁 5K")
        vm.onDate(vm.state.value.today)
        vm.onTime(LocalTime.of(19, 0))
        vm.onDistance(CrewRunDistanceChoice.KM5)
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        assertEquals(
            java.time.Instant.parse("2026-10-21T10:00:00Z"),
            assertNotNull(repo.lastCrewRun).meetAt,
            "10/21 19:00 KST"
        )
    }

    @Test
    fun `수정 - 글을 읽어 칸을 채우고 날짜·시간은 잠그며 제목·거리·반복만 저장한다(F-13d·F-13e)`() = runTest(main.dispatcher) {
        val vm = CrewRunWriteViewModel(repo, clock, seoul)
        vm.bind(CrewRunWriteMode.Edit(42))
        runCurrent()
        val s = vm.state.value
        assertEquals("토요일 한강 같이 뛰어요", s.title)
        assertEquals(LocalDate.of(2026, 10, 24), s.date)
        assertEquals(LocalTime.of(7, 0), s.time)
        assertEquals(CrewRunDistanceChoice.NO_LIMIT, s.distance)
        assertEquals(CrewRunRepeat.WEEKLY, s.repeat)
        vm.onSheet(CrewRunWriteSheet.DATE)
        assertNull(vm.state.value.sheet, "날짜 칸은 잠겨 있다")
        vm.onDate(LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 10, 24), vm.state.value.date)
        vm.onSheet(CrewRunWriteSheet.DISTANCE)
        assertEquals(CrewRunWriteSheet.DISTANCE, vm.state.value.sheet)
        vm.onDistance(CrewRunDistanceChoice.HALF)
        vm.onRepeat(CrewRunRepeat.NONE)
        vm.onTitle("하프 같이")
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        val edit = assertNotNull(repo.lastEdit)
        assertEquals("하프 같이", edit.title)
        assertEquals(Distance(21_097), edit.distance)
        assertEquals(CrewRunRepeat.NONE, edit.repeat)
    }

    @Test
    fun `수정 - 기존 1km 글은 거리를 바꾸지 않으면 1km로 저장한다(C4)`() = runTest(main.dispatcher) {
        repo.detailState = crewRunDetail.copy(distance = Distance(1_000))
        val vm = CrewRunWriteViewModel(repo, clock, seoul)
        vm.bind(CrewRunWriteMode.Edit(42))
        runCurrent()
        assertNull(vm.state.value.distance)
        assertEquals(Distance(1_000), vm.state.value.legacyDistance)
        assertTrue(vm.state.value.isValid)
        vm.events.test {
            vm.onSubmit()
            assertEquals(CrewRunWriteEvent.Posted, awaitItem())
        }
        assertEquals(Distance(1_000), assertNotNull(repo.lastEdit).distance)
    }
}

/** 크루런 일정 상세(피그마 F-13·F-13b·F-13c, W6). */
class CrewRunDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val clock = MutableClock()

    @Test
    fun `참석하기는 GOING을 보내고 참석 취소하기는 응답을 거둔 뒤 다시 읽는다`() = runTest(main.dispatcher) {
        val vm = CrewRunDetailViewModel(repo, clock)
        vm.bind(42)
        runCurrent()
        assertTrue(vm.state.value.canAnswer)
        assertFalse(assertNotNull(vm.state.value.detail).isGoing)
        vm.onAttend()
        runCurrent()
        assertTrue("vote:42:GOING" in repo.calls)
        assertTrue(assertNotNull(vm.state.value.detail).isGoing)
        vm.onCancelAttend()
        runCurrent()
        assertTrue("withdraw:42" in repo.calls)
        assertFalse(assertNotNull(vm.state.value.detail).isGoing)
    }

    @Test
    fun `지난 일정은 응답 버튼이 없고, 삭제를 확인하면 지운 뒤 앞 화면으로 간다(F-11d·F-13c)`() = runTest(main.dispatcher) {
        repo.detailState = crewRunDetail.copy(status = CrewRunStatus.FINISHED)
        val vm = CrewRunDetailViewModel(repo, clock)
        vm.bind(42)
        runCurrent()
        assertFalse(vm.state.value.canAnswer)
        vm.onMenu(true)
        vm.onDeleteAsk()
        assertTrue(vm.state.value.isDeleteAsked)
        assertFalse(vm.state.value.isMenuOpen)
        vm.events.test {
            vm.onDeleteConfirm()
            assertEquals(CrewRunDetailEvent.Deleted, awaitItem())
        }
        assertTrue("deletePost:42" in repo.calls)
    }

    @Test
    fun `읽지 못하면 오류 문구를 보이고 다시 시도로 읽는다`() = runTest(main.dispatcher) {
        repo.fail["crewRun"] = com.a207.runkk.app.core.model.AppError.Rejected("POST_NOT_FOUND")
        val vm = CrewRunDetailViewModel(repo, clock)
        vm.bind(42)
        runCurrent()
        assertNotNull(vm.state.value.loadError)
        repo.fail.clear()
        vm.refresh()
        runCurrent()
        assertNotNull(vm.state.value.detail)
    }
}
