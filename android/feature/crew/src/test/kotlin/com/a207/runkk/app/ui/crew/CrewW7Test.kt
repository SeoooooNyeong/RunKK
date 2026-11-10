package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.PastCrewRun
import com.a207.runkk.app.domain.crew.PastCrewRuns
import com.a207.runkk.app.domain.crew.PastGoals
import com.a207.runkk.app.domain.crew.PendingRequests
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** W7 시험 자료: 지난 목표 두 쪽, 지난 크루런 한 달(2026-10, 오늘 = 10-20). */
private val goalW3 = PastGoals.Goal(LocalDate.of(2026, 10, 12), 10, 3, Distance(80_000), Distance(72_000), false, 90, 6)
private val goalW2 = PastGoals.Goal(LocalDate.of(2026, 10, 5), 10, 2, Distance(50_000), Distance(61_500), true, 123, 7)
private val goalW1 = PastGoals.Goal(LocalDate.of(2026, 9, 28), 10, 1, Distance(40_000), Distance(10_000), false, 25, 3)
private val runFinished =
    PastCrewRun(40, "화요일 인터벌", Instant.parse("2026-10-13T10:00:00Z"), Distance(5_000), CrewRunStatus.FINISHED, 6, 6)
private val runCancelled =
    PastCrewRun(35, "토요일 하프", Instant.parse("2026-10-03T22:00:00Z"), Distance(21_097), CrewRunStatus.CANCELLED, 3, 0)
private val october = PastCrewRuns(
    YearMonth.of(2026, 10),
    setOf(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 4)),
    listOf(runFinished, runCancelled),
    YearMonth.of(2026, 9)
)

/** 크루 홈의 W7 동작(가입 신청·크루원 팝업·넘기기·내보내기·크루 삭제·정보 수정·내 정보 시트). 피그마 F-18~F-29, contract-W7. */
class CrewHomeW7ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()
    private val saved = mutableListOf<Pair<Boolean, Boolean>>()
    private var saveResult: Outcome<Unit> = Outcome.Success(Unit)

    private fun vm(role: CrewRole = CrewRole.LEADER) = CrewHomeViewModel(
        repo,
        ServerClock(MutableClock()),
        FakeCelebrationSharer(),
        { age, gender ->
            saved += age to gender
            saveResult
        }
    ).also {
        repo.role = role
        repo.memberList = membersWithMe
        it.bind(Routes.CrewHome(11))
    }

    @Test
    fun `크루 관리는 크루장만이고 운영진은 목표 설정만 남는다(C5)`() {
        assertTrue(vm(CrewRole.LEADER).state.value.canManage)
        val manager = vm(CrewRole.MANAGER).state.value
        assertFalse(manager.canManage)
        assertTrue(manager.canSetGoal)
    }

    @Test
    fun `크루장이 크루 정보를 열면 가입 신청을 읽고 승인하면 안내 뒤 다시 읽는다(F-25)`() {
        val vm = vm()
        vm.onPage(CrewHomePage.INFO)
        val request = vm.state.value.requests!!.requests.single()
        assertEquals("GOLD", request.tier)
        vm.onApprove(request)
        assertTrue(repo.calls.any { it.startsWith("approve") })
        assertEquals(uiText(R.string.crew_manage_approved, "새내기"), vm.state.value.message)
        assertEquals(2, repo.calls.count { it == "pending" })
    }

    @Test
    fun `가입 신청 알림(page INFO)으로 열면 크루 정보 쪽에서 가입 신청을 읽는다(리뷰 M6)`() {
        repo.role = CrewRole.LEADER
        repo.memberList = membersWithMe
        val vm = CrewHomeViewModel(repo, ServerClock(MutableClock()), FakeCelebrationSharer(), { _, _ -> saveResult })
        vm.bind(Routes.CrewHome(11, page = CrewHomePage.INFO.name))
        assertEquals(CrewHomePage.INFO, vm.state.value.page)
        assertEquals("GOLD", vm.state.value.requests!!.requests.single().tier)
    }

    @Test
    fun `크루원은 크루 정보를 열어도 가입 신청을 읽지 않는다`() {
        val vm = vm(CrewRole.MEMBER)
        vm.onPage(CrewHomePage.INFO)
        assertNull(vm.state.value.requests)
        assertTrue(repo.calls.none { it == "pending" })
    }

    @Test
    fun `메뉴는 쪽을 고르면 닫힌다(F-17)`() {
        val vm = vm()
        vm.onMenuOpen()
        assertTrue(vm.state.value.isMenuOpen)
        vm.onPage(CrewHomePage.MEMBERS)
        assertFalse(vm.state.value.isMenuOpen)
        assertEquals(CrewHomePage.MEMBERS, vm.state.value.page)
    }

    @Test
    fun `팝업에서 크루장 넘기기·내보내기는 확인 뒤에 보내고 취소하면 보내지 않는다(F-29b~d)`() {
        val vm = vm()
        val other = membersWithMe.members[1]
        vm.onMemberOpen(other)
        vm.onTransferAsk(other)
        assertNull(vm.state.value.openedMember)
        assertEquals(other, vm.state.value.confirmTransfer)
        vm.onTransferDismiss()
        assertTrue(repo.calls.none { it.startsWith("transfer") })
        vm.onKickAsk(other)
        vm.onKickConfirm()
        assertTrue("kick:2" in repo.calls)
        assertEquals(uiText(R.string.crew_manage_kicked, "달빛"), vm.state.value.message)
        vm.onTransferAsk(other)
        vm.onTransferConfirm()
        assertTrue(repo.calls.any { it.startsWith("transfer") })
    }

    @Test
    fun `크루원은 넘기기·내보내기 확인을 열 수 없고 내 줄도 대상이 아니다`() {
        val member = vm(CrewRole.MEMBER)
        member.onKickAsk(membersWithMe.members[1])
        assertNull(member.state.value.confirmKick)
        val leader = vm()
        leader.onTransferAsk(membersWithMe.members[0])
        assertNull(leader.state.value.confirmTransfer)
    }

    @Test
    fun `크루를 지우면 크루 탭으로 나간다(F-27, FR-116)`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.events.test {
            vm.onCrewDeleteConfirm()
            assertTrue(repo.calls.none { it.startsWith("delete") })
            vm.onCrewDeleteAsk()
            vm.onCrewDeleteConfirm()
            assertEquals(CrewHomeEvent.Left, awaitItem())
        }
    }

    @Test
    fun `정보 수정 로고 - 고른 사진을 미리 보이고 올린 주소를 저장에 싣는다(F-26, DV-성-6)`() {
        val vm = vm()
        vm.onPage(CrewHomePage.EDIT)
        vm.onEditLogoPicked(byteArrayOf(1, 2, 3))
        assertTrue("uploadLogo:3" in repo.calls)
        assertEquals(3, vm.state.value.editLogo?.size)
        assertFalse(vm.state.value.isUploadingLogo)
        assertEquals("/api/v1/crew-logos/7", vm.state.value.editForm.logoUrl)
        vm.onEditSave()
        assertEquals("/api/v1/crew-logos/7", repo.lastChangeInfo?.logoUrl)
        assertNull(vm.state.value.editLogo, "저장하면 미리보기를 비우고 크루 정보의 로고를 쓴다")
    }

    @Test
    fun `정보 수정 로고 - 올리지 못하면 미리보기를 비우고 원래 로고를 둔 채 안내한다(DV-성-6)`() {
        repo.fail["uploadLogo"] = AppError.Network
        val vm = vm()
        vm.onPage(CrewHomePage.EDIT)
        vm.onEditLogoPicked(byteArrayOf(1, 2, 3))
        assertNull(vm.state.value.editLogo)
        assertNull(vm.state.value.editForm.logoUrl, "원래 로고(없음)를 그대로 둔다")
        assertTrue(vm.state.value.message != null)
        vm.onEditLogoUnreadable()
        assertEquals(uiText(R.string.crew_create_logo_failed), vm.state.value.message)
    }

    @Test
    fun `탈퇴하면 크루 홈 상태를 비우고 같은 크루로 다시 묶으면 처음부터 읽는다(DV-성-7)`() = runTest(main.dispatcher) {
        val vm = vm(CrewRole.MEMBER)
        vm.onPage(CrewHomePage.INFO)
        vm.events.test {
            vm.onLeaveAsk()
            vm.onLeaveConfirm()
            assertEquals(CrewHomeEvent.Left, awaitItem())
        }
        assertNull(vm.state.value.detail, "떠난 크루의 정보를 남기지 않는다")
        assertEquals(CrewHomePage.HOME, vm.state.value.page)
        val before = repo.calls.count { it == "detail:11" }
        vm.bind(Routes.CrewHome(11))
        assertEquals(before + 1, repo.calls.count { it == "detail:11" })
    }

    @Test
    fun `역할이 바뀌면 탭을 옮길 때 크루 정보를 다시 읽어 목표 정하기를 보인다(DV-성 9(b))`() {
        val vm = vm(CrewRole.MEMBER)
        assertFalse(vm.state.value.canSetGoal)
        repo.role = CrewRole.LEADER
        vm.onTab(CrewTab.CHAT)
        vm.onTab(CrewTab.RACE)
        assertTrue(vm.state.value.canSetGoal)
    }

    @Test
    fun `크루 정보 쪽을 열 때마다 크루 정보(인원)를 다시 읽는다(DV-성 9(d))`() {
        val vm = vm(CrewRole.MEMBER)
        val before = repo.calls.count { it == "detail:11" }
        vm.onPage(CrewHomePage.INFO)
        assertEquals(before + 1, repo.calls.count { it == "detail:11" })
    }

    @Test
    fun `정보 수정은 크루 정보로 채워 열고 저장하면 크루 정보 쪽으로 돌아간다(F-26)`() {
        val vm = vm()
        vm.onPage(CrewHomePage.EDIT)
        assertEquals("새벽질주", vm.state.value.editForm.name)
        vm.onEditForm(vm.state.value.editForm.copy(name = "새벽질주2"))
        vm.onEditSave()
        assertTrue("changeInfo:새벽질주2" in repo.calls)
        assertEquals(CrewHomePage.INFO, vm.state.value.page)
        assertEquals(uiText(R.string.crew_manage_info_changed), vm.state.value.message)
    }

    @Test
    fun `내 정보 시트는 내 공개 설정으로 열고 토글만 바꾸면 계정 공개 설정만 저장한다(F-23)`() {
        val vm = vm()
        vm.onPage(CrewHomePage.MEMBERS)
        vm.onIntroEdit()
        assertTrue(vm.state.value.isAgePublic)
        assertFalse(vm.state.value.isGenderPublic)
        vm.onGenderPublic(true)
        vm.onIntroSave()
        assertEquals(listOf(true to true), saved)
        assertTrue(repo.calls.none { it.startsWith("intro:") })
        assertFalse(vm.state.value.isEditingIntro)
    }

    @Test
    fun `소개와 토글을 함께 바꾸고 공개 설정 저장이 실패하면 시트를 연 채 안내한다`() {
        saveResult = Outcome.Failure(AppError.Network)
        val vm = vm()
        vm.onPage(CrewHomePage.MEMBERS)
        vm.onIntroEdit()
        vm.onIntroDraft("주말 장거리")
        vm.onAgePublic(false)
        vm.onIntroSave()
        assertTrue("intro:주말 장거리" in repo.calls)
        assertTrue(vm.state.value.isEditingIntro)
        assertTrue(vm.state.value.message != null)
    }

    @Test
    fun `아무것도 바꾸지 않고 저장하면 보내지 않고 닫는다`() {
        val vm = vm()
        vm.onPage(CrewHomePage.MEMBERS)
        vm.onIntroEdit()
        vm.onIntroSave()
        assertTrue(saved.isEmpty())
        assertTrue(repo.calls.none { it.startsWith("intro:") })
        assertFalse(vm.state.value.isEditingIntro)
    }

    @Test
    fun `내 공개 설정을 모르면(옛 서버) 토글을 바꿔도 계정 공개 설정을 저장하지 않는다(리뷰 HIGH)`() {
        val vm = vm()
        repo.memberList = membersWithMe.copy(
            members = membersWithMe.members.map {
                if (it.isMe) it.copy(isAgePublic = null, isGenderPublic = null) else it
            }
        )
        vm.onPage(CrewHomePage.MEMBERS)
        vm.refresh()
        vm.onIntroEdit()
        vm.onGenderPublic(true)
        vm.onIntroSave()
        assertTrue(saved.isEmpty())
    }
}

/** 지난 목표(피그마 F-21, contract-W7 4장). */
class CrewPastGoalsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `첫 쪽을 읽고 끝까지 내리면 nextBefore로 다음 쪽을 이어 받는다`() {
        repo.pastGoalPages[null] = PastGoals(listOf(goalW3, goalW2), goalW2.weekStart)
        repo.pastGoalPages[goalW2.weekStart] = PastGoals(listOf(goalW1), null)
        val vm = CrewPastGoalsViewModel(repo).also { it.bind(11) }
        assertEquals(listOf(goalW3, goalW2), vm.state.value.goals)
        vm.onLoadMore()
        assertEquals(listOf(goalW3, goalW2, goalW1), vm.state.value.goals)
        assertNull(vm.state.value.nextBefore)
        vm.onLoadMore()
        assertEquals(2, repo.calls.count { it.startsWith("pastGoals") })
    }

    @Test
    fun `첫 쪽이 실패하면 오류를 보이고 다시 읽는다`() {
        repo.fail["pastGoals"] = AppError.Network
        val vm = CrewPastGoalsViewModel(repo).also { it.bind(11) }
        assertTrue(vm.state.value.error != null)
        repo.fail.clear()
        vm.load()
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.isLoaded)
    }
}

/** 지난 크루런(피그마 F-20·F-20b·F-20c, contract-W6). 오늘 = 2026-10-20(KST). */
class CrewPastRunsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm() = CrewPastRunsViewModel(repo, MutableClock()).also {
        repo.pastRunMonths[YearMonth.of(2026, 10)] = october
        it.bind(11)
    }

    @Test
    fun `이번 달부터 보이고 다음 달로는 가지 못한다`() {
        val vm = vm()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(LocalDate.of(2026, 10, 20), vm.state.value.today)
        assertFalse(vm.state.value.canGoNext)
        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
    }

    @Test
    fun `가장 이른 달까지만 앞으로 가고 달을 바꾸면 그 달을 읽는다`() {
        val vm = vm()
        vm.onPrevMonth()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
        assertTrue("pastRuns:2026-09" in repo.calls)
        vm.onPrevMonth()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
    }

    @Test
    fun `날짜를 고르면 그날 것만, 다시 누르면 달 전체(F-20c)`() {
        val vm = vm()
        vm.onDate(LocalDate.of(2026, 10, 13))
        assertEquals(listOf(runFinished), vm.state.value.shownRuns)
        vm.onDate(LocalDate.of(2026, 10, 13))
        assertEquals(listOf(runFinished, runCancelled), vm.state.value.shownRuns)
    }

    @Test
    fun `기간 선택은 범위 밖을 범위 끝으로 맞춘다(F-20b)`() {
        val vm = vm()
        vm.onPeriodOpen()
        vm.onPeriodApply(YearMonth.of(2027, 3))
        assertFalse(vm.state.value.isPeriodSheetOpen)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        vm.onPeriodApply(YearMonth.of(2025, 1))
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
    }
}

/** W7 화면: 문구·동작 확인과 스냅숏(피그마 F-17~F-29). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewW7ScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun info(role: CrewRole) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = role),
        page = CrewHomePage.INFO,
        members = membersWithMe,
        requests = if (role == CrewRole.LEADER) {
            PendingRequests(
                listOf(
                    PendingRequests.Request(91, 9, "새내기", start, tier = "GOLD", level = 23, ageBand = "20대"),
                    PendingRequests.Request(92, 10, "언덕왕", start, tier = "UNRANKED", level = 4)
                )
            )
        } else {
            null
        }
    )

    @Test
    fun `정보 수정의 로고 연필 덮개를 누르면 사진 고르기를 연다(F-26, DV-성-6)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    info(CrewRole.LEADER).copy(page = CrewHomePage.EDIT, editForm = crewDetail().toFormState()),
                    CrewHomeActions(onEditLogoPick = { calls += "pickLogo" })
                )
            }
        }
        compose.onNodeWithContentDescription("크루 이미지").performClick()
        assertEquals(listOf("pickLogo"), calls)
    }

    @Test
    fun `올린 로고가 있으면 크루 홈 머리와 크루 정보가 그 로고를 읽는다(DV-성-8)`() {
        val asked = mutableListOf<String>()
        val loader: suspend (String) -> Outcome<ByteArray> = { url ->
            asked += url
            Outcome.Failure(AppError.Network)
        }
        val withLogo = crewDetail(role = CrewRole.LEADER).copy(logoUrl = "/api/v1/crew-logos/1")
        var page by mutableStateOf(CrewHomePage.HOME)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCrewLogoLoader provides loader) {
                    CrewHomeScreen(info(CrewRole.LEADER).copy(detail = withLogo, page = page), CrewHomeActions())
                }
            }
        }
        compose.waitForIdle()
        assertEquals(listOf("/api/v1/crew-logos/1"), asked.distinct(), "크루 홈 머리")
        asked.clear()
        page = CrewHomePage.INFO
        compose.waitForIdle()
        assertTrue("/api/v1/crew-logos/1" in asked, "크루 정보")
    }

    @Test
    fun `크루원 크루 정보는 누적 통계·지난 기록 메뉴·탈퇴만 보이고 크루장 영역은 없다(F-18)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    info(CrewRole.MEMBER),
                    CrewHomeActions(onPastRuns = { calls += "runs" }, onPastGoals = { calls += "goals" })
                )
            }
        }
        compose.onNodeWithText("1,284.67km").assertExists()
        compose.onNodeWithText("12회").assertExists()
        compose.onNodeWithText("수정").assertDoesNotExist()
        compose.onNodeWithText("크루 삭제하기").assertDoesNotExist()
        compose.onNodeWithText("지난 크루런").performClick()
        compose.onNodeWithText("지난 목표").performClick()
        assertEquals(listOf("runs", "goals"), calls)
    }

    @Test
    fun `크루장 크루 정보는 가입 신청 거절·승인과 수정·크루 삭제를 본다(F-25)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    info(CrewRole.LEADER),
                    CrewHomeActions(
                        onApprove = { calls += "approve:${it.requestId}" },
                        onReject = { calls += "reject:${it.requestId}" },
                        onPage = { calls += "page:$it" },
                        onCrewDeleteAsk = { calls += "delete" }
                    )
                )
            }
        }
        compose.onNodeWithText("2건").assertExists()
        compose.onNodeWithContentDescription("새내기 신청 승인").performScrollTo().performClick()
        compose.onNodeWithContentDescription("언덕왕 신청 거절").performScrollTo().performClick()
        compose.onNodeWithText("수정").performClick()
        compose.onNodeWithText("크루 삭제하기").performClick()
        assertEquals(listOf("approve:91", "reject:92", "page:EDIT", "delete"), calls)
    }

    @Test
    fun `초대 코드 팝업은 복사와 닫기 ✕만 있고 재발급·기한은 없다(F-18b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    info(CrewRole.LEADER).copy(isInviteOpen = true),
                    CrewHomeActions(onInviteClose = { calls += "close" })
                )
            }
        }
        compose.onNodeWithText("복사").assertExists()
        compose.onNodeWithText("새로 발급").assertDoesNotExist()
        compose.onNodeWithContentDescription("초대 코드 닫기").performClick()
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun `크루원 팝업 - 크루장은 넘기기·내보내기를 보고 크루원은 정보만 본다(F-22b·F-29b)`() {
        var role by mutableStateOf(CrewRole.LEADER)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    info(role).copy(page = CrewHomePage.MEMBERS, openedMember = membersWithMe.members[1]),
                    CrewHomeActions(onTransferAsk = { calls += "transfer:${it.memberId}" }, onKickAsk = {
                        calls +=
                            "kick"
                    })
                )
            }
        }
        compose.onNodeWithText("가입일").assertExists()
        compose.onNodeWithText("2026. 10. 02").assertExists()
        compose.onNodeWithText("크루장 넘기기").performClick()
        compose.onNodeWithText("내보내기").performClick()
        assertEquals(listOf("transfer:2", "kick"), calls)
        role = CrewRole.MEMBER
        compose.waitForIdle()
        compose.onNodeWithText("크루장 넘기기").assertDoesNotExist()
    }

    @Test
    fun `내보내기 확인은 피그마 문구다(F-29d, C10)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(info(CrewRole.LEADER).copy(confirmKick = membersWithMe.members[1]), CrewHomeActions())
            }
        }
        compose.onNodeWithText("달빛 님을 내보낼까요?").assertExists()
        compose.onNodeWithText("내보낸 크루원은 다시 가입 신청을 해야 해요.").assertExists()
    }

    @Test
    fun infoMember() = compose.captureRunkkScreen("W7_F18_crew_info_member", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.MEMBER), CrewHomeActions())
    }

    @Test
    fun infoLeader() = compose.captureRunkkScreen("W7_F25_crew_info_leader", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.LEADER), CrewHomeActions())
    }

    @Test
    fun inviteCode() = compose.captureRunkkScreen("W7_F25b_invite_code", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.LEADER).copy(isInviteOpen = true), CrewHomeActions())
    }

    @Test
    fun menu() = compose.captureRunkkScreen("W7_F17_crew_menu", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.MEMBER).copy(page = CrewHomePage.HOME, isMenuOpen = true), CrewHomeActions())
    }

    @Test
    fun membersLeader() = compose.captureRunkkScreen("W7_F29_crew_members_leader", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.LEADER).copy(page = CrewHomePage.MEMBERS), CrewHomeActions())
    }

    @Test
    fun memberPopupLeader() = compose.captureRunkkScreen("W7_F29b_member_popup_leader", isDarkTheme = false) {
        CrewHomeScreen(
            info(CrewRole.LEADER).copy(page = CrewHomePage.MEMBERS, openedMember = membersWithMe.members[1]),
            CrewHomeActions()
        )
    }

    @Test
    fun memberPopupDark() = compose.captureRunkkScreen("W7_F22b_member_popup_dark") {
        CrewHomeScreen(
            info(CrewRole.MEMBER).copy(page = CrewHomePage.MEMBERS, openedMember = membersWithMe.members[0]),
            CrewHomeActions()
        )
    }

    @Test
    fun transferConfirm() = compose.captureRunkkScreen("W7_F29c_transfer_confirm", isDarkTheme = false) {
        CrewHomeScreen(
            info(CrewRole.LEADER).copy(page = CrewHomePage.MEMBERS, confirmTransfer = membersWithMe.members[1]),
            CrewHomeActions()
        )
    }

    @Test
    fun myInfoSheet() = compose.captureRunkkScreen("W7_F23_my_info_sheet", isDarkTheme = false) {
        CrewHomeScreen(
            info(CrewRole.LEADER).copy(
                page = CrewHomePage.MEMBERS,
                isEditingIntro = true,
                introDraft = "매일 새벽 5시",
                isAgePublic = true
            ),
            CrewHomeActions()
        )
    }

    @Test
    fun edit() = compose.captureRunkkScreen("W7_F26_crew_edit", isDarkTheme = false) {
        CrewHomeScreen(
            info(CrewRole.LEADER).copy(page = CrewHomePage.EDIT, editForm = crewDetail().toFormState()),
            CrewHomeActions()
        )
    }

    @Test
    fun leaveBlocked() = compose.captureRunkkScreen("W7_F28_leave_blocked", isDarkTheme = false) {
        CrewHomeScreen(info(CrewRole.LEADER).copy(isLeaveBlocked = true), CrewHomeActions())
    }

    private val goalsState = CrewPastGoalsUiState(crewId = 11, goals = listOf(goalW3, goalW2, goalW1), isLoaded = true)

    @Test
    fun `지난 목표 카드는 주차·달성·100 초과 진행률·참여 인원을 읽는다(F-21, C2)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewPastGoalsScreen(goalsState, onBack = {}, onRetry = {}, onLoadMore = {}, onMessageShown = {})
            }
        }
        compose.onNodeWithContentDescription(
            "10월 2주차, 달성, 50킬로미터 중 61.5킬로미터, 123퍼센트, 참여 7명"
        ).assertExists()
        compose.onNodeWithContentDescription(
            "10월 3주차, 달성 실패, 80킬로미터 중 72.0킬로미터, 90퍼센트, 참여 6명"
        ).assertExists()
    }

    @Test
    fun pastGoals() = compose.captureRunkkScreen("W7_F21_past_goals", isDarkTheme = false) {
        CrewPastGoalsScreen(goalsState, onBack = {}, onRetry = {}, onLoadMore = {}, onMessageShown = {})
    }

    private val runsState = CrewPastRunsUiState(
        crewId = 11,
        today = LocalDate.of(2026, 10, 20),
        month = YearMonth.of(2026, 10),
        runs = october.runs,
        runDays = october.days,
        earliestMonth = october.earliestMonth
    )

    @Test
    fun `지난 크루런 - 줄을 누르면 일정 상세로 열고 취소된 크루런은 취소됨이다(F-20)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewPastRunsScreen(
                    runsState,
                    CrewPastRunsActions(onRun = { calls += "run:${it.postId}" }, onPeriodOpen = { calls += "period" })
                )
            }
        }
        compose.onNodeWithText("10월 크루런").assertExists()
        compose.onNodeWithText("화요일 인터벌").performClick()
        compose.onNodeWithText("토요일 하프").performClick()
        compose.onNodeWithContentDescription("기간 선택, 지금 2026년 10월").performClick()
        compose.onNodeWithText("취소됨", useUnmergedTree = true).assertExists()
        assertEquals(listOf("run:40", "run:35", "period"), calls)
    }

    @Test
    fun pastRuns() = compose.captureRunkkScreen("W7_F20_past_runs", isDarkTheme = false) {
        CrewPastRunsScreen(runsState, CrewPastRunsActions())
    }

    @Test
    fun pastRunsDay() = compose.captureRunkkScreen("W7_F20c_past_runs_day", isDarkTheme = false) {
        CrewPastRunsScreen(runsState.copy(selectedDate = LocalDate.of(2026, 10, 13)), CrewPastRunsActions())
    }

    @Test
    fun pastRunsPeriod() = compose.captureRunkkScreen("W7_F20b_period_sheet", isDarkTheme = false) {
        CrewPastRunsScreen(runsState.copy(isPeriodSheetOpen = true), CrewPastRunsActions())
    }
}
