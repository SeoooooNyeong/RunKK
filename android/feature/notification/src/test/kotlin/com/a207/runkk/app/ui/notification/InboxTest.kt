package com.a207.runkk.app.ui.notification

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class FakeNotifications : NotificationRepository {
    var inboxResult: Outcome<Inbox> = Outcome.Success(Inbox(sampleItems))
    val opened = mutableListOf<Long>()

    override suspend fun inbox(): Outcome<Inbox> = inboxResult

    var openResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun open(notificationId: Long): Outcome<Unit> {
        opened += notificationId
        return openResult
    }
}

private val now: Instant = Instant.parse("2026-10-21T03:00:00Z") // 수요일 12시(서울)

internal val sampleItems = listOf(
    Inbox.Item(
        1,
        "CREW_RUN_SOON",
        "크루런 곧 시작",
        "새벽크루 크루런이 10분 뒤 시작해요",
        now.minusSeconds(600),
        isRead = false,
        screen = "CREW_HOME",
        targetId = "7"
    ),
    Inbox.Item(
        2,
        "PLAN_MISSED",
        "오늘 계획을 놓쳤어요",
        "보충 거리 2km를 추천해요",
        Instant.parse("2026-10-19T11:00:00Z"),
        isRead = true,
        screen = null
    ),
    Inbox.Item(
        3,
        "BATTLE_CORRECTED",
        "대결 결과가 정정됐어요",
        "상대 기록 판정이 바뀌었어요",
        Instant.parse("2026-10-01T11:00:00Z"),
        isRead = true,
        screen = "BATTLE_RESULT",
        targetId = "12"
    )
)

private fun str(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InboxTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeNotifications()
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `알림을 오늘·이번 주·그 전으로 묶는다(FR-178)`() {
        val vm = InboxViewModel(repo, clock, FakeEventRepository())
        val groups = vm.state.value.groups
        assertEquals(
            listOf(
                R.string.notification_group_today,
                R.string.notification_group_this_week,
                R.string.notification_group_earlier
            ),
            groups.map { it.titleRes }
        )
        assertEquals(listOf(1L), groups[0].items.map { it.id })
        assertEquals(1, vm.state.value.unreadCount)
    }

    @Test
    fun `앱 시계가 UTC여도 오늘·이번 주는 기기 시간대로 묶는다(MR !91 e2e 2 후속)`() {
        // 한국 11/08 00:30 = UTC 11/07 15:30. 한국 11/07 23:30에 받은 알림은 오늘이 아니라 이번 주다
        val utc = Clock.fixed(Instant.parse("2026-11-07T15:30:00Z"), java.time.ZoneOffset.UTC)
        repo.inboxResult = Outcome.Success(
            Inbox(listOf(sampleItems[0].copy(receivedAt = Instant.parse("2026-11-07T14:30:00Z"))))
        )
        val vm = InboxViewModel(repo, utc, FakeEventRepository(), ZoneId.of("Asia/Seoul"))

        val groups = vm.state.value.groups
        assertEquals(listOf(R.string.notification_group_this_week), groups.map { it.titleRes })
        assertEquals(ZoneId.of("Asia/Seoul"), vm.state.value.zone)
    }

    @Test
    fun `알림을 누르면 읽음으로 바꾸고 이동 경로로 간다`() = runTest {
        val vm = InboxViewModel(repo, clock, FakeEventRepository())
        vm.events.test {
            vm.onItemTapped(sampleItems[0])
            assertEquals(InboxEvent.Navigate(Routes.CrewHome(7)), awaitItem())
        }
        assertEquals(listOf(1L), repo.opened)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test
    fun `알림함을 못 읽으면 오류를 보인다`() {
        repo.inboxResult = Outcome.Failure(AppError.Network)
        val vm = InboxViewModel(repo, clock, FakeEventRepository())
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
    }

    @Test
    fun `화면은 묶음 제목 없이 종류 라벨, 본문, 상대 시간을 보이고 누르면 알린다(F7)`() {
        val tapped = mutableListOf<Long>()
        val state = InboxUiState(
            isLoading = false,
            groups = groupInbox(sampleItems, clock),
            now = now,
            zone = clock.zone
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InboxScreen(state, onBack = {}, onItem = { tapped += it.id }, onRetry = {})
            }
        }
        compose.onNodeWithText(str(R.string.notification_group_this_week)).assertDoesNotExist()
        compose.onNodeWithText("크루런").assertExists()
        compose.onNodeWithText("10분 전").assertExists()
        compose.onNodeWithText("2일 전").assertExists()
        compose.onNodeWithText("새벽크루 크루런이 10분 뒤 시작해요").performClick()
        assertEquals(listOf(1L), tapped)
    }

    @Test
    fun `알림이 없으면 빈 상태`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InboxScreen(InboxUiState(isLoading = false), onBack = {}, onItem = {}, onRetry = {})
            }
        }
        compose.onNodeWithText(str(R.string.notification_empty_title)).assertExists()
    }

    @Test
    fun `읽음 처리가 실패하면 안 읽음으로 되돌리고 알린다`() {
        repo.openResult = Outcome.Failure(AppError.Network)
        val vm = InboxViewModel(repo, clock, FakeEventRepository())
        vm.onItemTapped(sampleItems[0])
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.message)
    }

    @Test
    fun `TC-6535 알림 이동 표 - 새 화면 5종과 대상 ID, 모르는 화면은 알림함에 머문다(FR-178, D-150)`() {
        // 공지·투표·일정 알림의 대상 ID는 글 ID라 크루 홈 경로로 쓰지 않는다: 내 크루 홈의 해당 탭(A3b 경로)으로 연다.
        assertEquals(Routes.Key(Routes.CREW_NOTICE), inboxDestination("CREW_NOTICE", "3"))
        assertEquals(Routes.Key(Routes.CREW_POLL), inboxDestination("CREW_POLL", "3"))
        assertEquals(Routes.Key(Routes.CREW_SCHEDULE), inboxDestination("CREW_SCHEDULE", "3"))
        assertEquals(Routes.Key(Routes.CREW_NOTICE), inboxDestination("CREW_NOTICE", null))
        // 채팅 알림의 대상 ID는 크루 ID다(서버 ChatDeliveryListener). 없으면 내 크루를 확인하는 채팅 경로로.
        assertEquals(Routes.CrewHome(3, "CHAT"), inboxDestination("CREW_CHAT", "3"), "채팅은 크루 홈 채팅 탭(D-성-21)")
        assertEquals(Routes.Key(Routes.CREW_CHAT), inboxDestination("CREW_CHAT", null))
        assertEquals(Routes.Key(Routes.AVATAR_BACKGROUND), inboxDestination("AVATAR_BACKGROUND", null))
        assertEquals(Routes.CrewHome(7), inboxDestination("CREW_HOME", "7"))
        assertEquals(Routes.BattleResult(12), inboxDestination("BATTLE_RESULT", "12"))
        assertEquals(Routes.Key(Routes.RECORDS_BATTLES), inboxDestination("BATTLE_RESULT", "x"))
        assertEquals(Routes.CrewHome(4), inboxDestination("CREW_MANAGE", "4"))
        assertEquals(null, inboxDestination("CREW_BOARD", "3"))
        assertEquals(null, inboxDestination("SOMETHING_NEW", null))
        assertEquals(null, inboxDestination(null, null))
    }

    /**
     * TC-6535 "Routes가 새로 더한 상수": v0.11에서 Routes에 더한 경로는 알림 화면 5종만이 아니다. A3a가 GUEST_START·AVATAR_BACKGROUND,
     * A3b가 CrewChat·CrewPollWrite·CrewBoardWrite·CREW_NOTICE·CREW_POLL·CREW_SCHEDULE·CREW_CHAT을, v0.10 와이어프레임(M1·M3·M7·M8)이
     * CrewNoticeWrite·CrewPoll·CrewMeetupDetail·CrewGoalSet을 더했다(실제 목록에 맞춘다).
     */
    @Test
    fun `TC-6535 Routes의 경로 이름과 인자 키는 이 목록이다(v0·11 9개, 와이어프레임 v0·10 5개)`() {
        val constants = Routes::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.name }
            .toSet()
        val keys = Routes::class.java.declaredClasses.map { it.simpleName }.toSet()
        assertEquals(
            V010_CONSTANTS + V011_CONSTANTS + V014_CONSTANTS + V018_CONSTANTS + V0114_CONSTANTS + "COACH_CONSENTS" +
                "COACH_CHAT" +
                "AVATAR_RUNNER" +
                "CREW_SEARCH",
            constants
        )
        assertEquals(
            V010_KEYS + V011_KEYS + WIRE_V010_KEYS + V014_KEYS + V018_KEYS + V0114_KEYS + W6_KEYS + W7_KEYS,
            keys
        )
    }

    @Test
    fun `알림 이동 표는 서버 Screen 11개를 모두 다루고 EXTERNAL_LINK는 링크로 연다`() {
        assertEquals(ContractEnums.names("Screen"), INBOX_SCREENS + LINK_SCREEN)
        INBOX_SCREENS.forEach { assertNotNull(inboxDestination(it, "1"), it) }
        assertNull(inboxDestination(LINK_SCREEN, FORM))
    }

    @Test
    fun `공지 링크는 EXTERNAL_LINK의 https 주소만 열고 나머지는 알림함에 머문다`() {
        assertEquals(FORM, inboxLink(LINK_SCREEN, FORM))
        assertNull(inboxLink(LINK_SCREEN, null))
        assertNull(inboxLink(LINK_SCREEN, "http://forms.gle/AbCdEf123"))
        assertNull(inboxLink(LINK_SCREEN, "intent://forms.gle/x#Intent;end"))
        assertNull(inboxLink(LINK_SCREEN, "https://forms.gle/a b"))
        assertNull(inboxLink("CREW_HOME", FORM))
    }

    @Test
    fun `공지를 누르면 읽음으로 바꾸고 링크를 연다, 링크가 없으면 알림함에 머문다`() = runTest {
        val vm = InboxViewModel(repo, clock, FakeEventRepository())
        vm.events.test {
            vm.onItemTapped(notice(id = 11, link = FORM))
            assertEquals(InboxEvent.OpenLink(FORM), awaitItem())
            vm.onItemTapped(notice(id = 12, link = null))
            expectNoEvents()
        }
        assertEquals(listOf(11L, 12L), repo.opened)
    }

    @Test
    fun `공지 줄은 라벨 대신 서버가 준 공지 제목과 본문을 보인다`() {
        repo.inboxResult = Outcome.Success(Inbox(listOf(notice(id = 11, link = FORM))))
        val state = InboxViewModel(repo, clock, FakeEventRepository()).state.value
        compose.setContent {
            RunkkTheme { InboxScreen(state, onBack = {}, onItem = {}, onRetry = {}) }
        }
        compose.onNodeWithText("코치님 이벤트 당첨").assertExists()
        compose.onNodeWithText("구글 폼에 연락처를 남겨 주세요").assertExists()
    }

    @Test
    fun `공지 행은 링크가 있을 때만 링크 열기 동작 이름을 달고 시간을 함께 보인다(피그마 공지 행)`() {
        repo.inboxResult = Outcome.Success(Inbox(listOf(notice(id = 11, link = FORM), notice(id = 12, link = null))))
        val state = InboxViewModel(repo, clock, FakeEventRepository()).state.value
        compose.setContent {
            RunkkTheme { InboxScreen(state, onBack = {}, onItem = {}, onRetry = {}) }
        }
        val withLabel = SemanticsMatcher("링크 열기 동작") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "링크 열기"
        }
        compose.onAllNodes(withLabel).assertCountEquals(1)
        compose.onAllNodesWithText("1분 전").assertCountEquals(2)
    }

    private fun notice(id: Long, link: String?) = Inbox.Item(
        id,
        "NOTICE",
        "코치님 이벤트 당첨",
        "구글 폼에 연락처를 남겨 주세요",
        now.minusSeconds(60),
        isRead = false,
        screen = LINK_SCREEN,
        targetId = link
    )

    private companion object {
        const val FORM = "https://forms.gle/AbCdEf123"
        val V010_CONSTANTS = setOf(
            "LOGIN", "SIGN_UP", "HOME", "CREW", "ME", "RUN", "PROFILE_EDIT", "SETTINGS",
            "FREE_RUN_SETTINGS", "NOTIFICATION_SETTINGS", "CONSENTS", "WITHDRAW", "RECORDS", "GROWTH", "REPORT",
            "RANKING", "COACH", "NOTIFICATIONS", "BATTLE_RANDOM", "RUNIMAL", "AVATAR", "AVATAR_SETUP",
            "SHOP", "CREW_CREATE"
        )
        val V011_CONSTANTS = setOf(
            "GUEST_START",
            "AVATAR_BACKGROUND",
            "CREW_NOTICE",
            "CREW_POLL",
            "CREW_SCHEDULE",
            "CREW_CHAT"
        )

        /**
         * v0.14 하단 탭 재구성(D-250), 기록 화면 대결 탭(D-281, BATTLE_HISTORY를 대신함), Runimal 16유형(D-285)이 더한 경로.
         * v0.14 배틀런 화면(BATTLE_MODE, D-268)은 v0.18에서 랜덤·친구 대결로 나눠 지웠다(D-498, CR-142).
         */
        val V014_CONSTANTS = setOf("RECORD_RUN", "RECORDS_BATTLES", "RUNIMAL_TYPES")

        /** 0.11.4 이벤트(S15P31A207-275): 이벤트 목록, 셀럽런 분절로 여는 기록런, 이벤트 상세 웹 페이지. */
        val V0114_CONSTANTS = setOf("EVENTS", "RECORD_RUN_CELEB")
        val V0114_KEYS = setOf("EventDetail")

        /** 피그마 맞춤 W6(S15P31A207-297): 크루런 일정 상세·수정, 지난 크루런(화면은 W7). */
        val W6_KEYS = setOf("CrewRunDetail", "CrewRunEdit", "CrewPastRuns")
        val V010_KEYS = setOf(
            "Key", "RunResult", "RecordDetail", "RecordCard", "BattleRunning", "BattleQuickMatch", "BattleResult",
            "CrewHome", "CrewRunWrite", "CrewRunLobby", "CrewRunResult"
        )

        /** 피그마 맞춤 W7: 크루 관리(CrewManage)를 지우고 지난 목표를 더했다(지난 크루런 경로는 W6). */
        val W7_KEYS = setOf("CrewPastGoals")

        // W5([임시:D-성-21]): 채팅은 크루 홈 탭이 되어 CrewChat 경로를 지웠다.
        val V011_KEYS = setOf("CrewPollWrite", "CrewBoardWrite")

        /** 와이어프레임 v0.10 반영에서 더한 인자 키(F8 다른 러너 프로필, M1 공지 쓰기, M3 투표, M7 대면 모임, M8 공동 목표). */
        val WIRE_V010_KEYS = setOf("MemberProfile", "CrewNoticeWrite", "CrewPoll", "CrewMeetupDetail", "CrewGoalSet")

        /**
         * v0.14 유형 상세(D-285)·평가 결과(D-284)와 친구 대결 초대 대기·참가 확인(D-269, D-272) 인자 키.
         * 셀프·셀럽 탭 경로(SELF·CELEB)는 기록런이 대신해 지웠다.
         */
        val V014_KEYS = setOf("RunimalType", "Evaluation", "FriendInviteWait", "FriendInviteJoin")

        /** v0.18 배치고사·대결 분리(D-498): 친구 대결, 배치고사 진행·완료 경로와 판 결과 인자 키(S15P31A207-275). */
        val V018_CONSTANTS = setOf("BATTLE_FRIEND", "PLACEMENT", "PLACEMENT_COMPLETE")
        val V018_KEYS = setOf("PlacementResult")
    }
}
