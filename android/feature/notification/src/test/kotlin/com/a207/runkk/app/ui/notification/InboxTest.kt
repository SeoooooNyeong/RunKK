package com.a207.runkk.app.ui.notification

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        read = false,
        route = "crew"
    ),
    Inbox.Item(
        2,
        "PLAN_MISSED",
        "오늘 계획을 놓쳤어요",
        "보충 거리 2km를 추천해요",
        Instant.parse("2026-10-19T11:00:00Z"),
        read = true,
        route = null
    ),
    Inbox.Item(
        3,
        "BATTLE_CORRECTED",
        "대결 결과가 정정됐어요",
        "상대 기록 판정이 바뀌었어요",
        Instant.parse("2026-10-01T11:00:00Z"),
        read = true,
        route = "battle/history"
    )
)

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
        val vm = InboxViewModel(repo, clock)
        val groups = vm.state.value.groups
        assertEquals(listOf("오늘", "이번 주", "그 전"), groups.map { it.title })
        assertEquals(listOf(1L), groups[0].items.map { it.id })
        assertEquals(1, vm.state.value.unreadCount)
    }

    @Test
    fun `알림을 누르면 읽음으로 바꾸고 이동 경로로 간다`() = runTest {
        val vm = InboxViewModel(repo, clock)
        vm.events.test {
            vm.onItemTapped(sampleItems[0])
            assertEquals(InboxEvent.Navigate("crew"), awaitItem())
        }
        assertEquals(listOf(1L), repo.opened)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test
    fun `알림함을 못 읽으면 오류를 보인다`() {
        repo.inboxResult = Outcome.Failure(AppError.Network)
        val vm = InboxViewModel(repo, clock)
        assertTrue(vm.state.value.error!!.startsWith("네트워크"))
    }

    @Test
    fun `화면은 묶음 제목과 알림을 보이고 누르면 알린다`() {
        val tapped = mutableListOf<Long>()
        val state = InboxUiState(loading = false, groups = groupInbox(sampleItems, clock))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InboxScreen(state, onBack = {}, onItem = {
                    tapped +=
                        it.id
                }, onRetry = {})
            }
        }
        compose.onNodeWithText("이번 주").assertExists()
        compose.onNodeWithText("크루런 곧 시작").performClick()
        assertEquals(listOf(1L), tapped)
    }

    @Test
    fun `알림이 없으면 빈 상태`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InboxScreen(InboxUiState(loading = false), onBack = {}, onItem = {}, onRetry = {})
            }
        }
        compose.onNodeWithText("새 알림이 없어요").assertExists()
    }

    @Test
    fun `읽음 처리가 실패하면 안 읽음으로 되돌리고 알린다`() {
        repo.openResult = Outcome.Failure(AppError.Network)
        val vm = InboxViewModel(repo, clock)
        vm.onItemTapped(sampleItems[0])
        assertEquals(1, vm.state.value.unreadCount)
        assertTrue(vm.state.value.message!!.startsWith("네트워크"))
    }
}
