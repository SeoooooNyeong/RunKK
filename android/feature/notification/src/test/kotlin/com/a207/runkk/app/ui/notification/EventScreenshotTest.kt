package com.a207.runkk.app.ui.notification

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.notification.Inbox
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 이벤트 목록(피그마 I-3b 62271:6986 Light, 62274:6818 Dark)과 알림함 이벤트 행, 공지 행 스크린샷. 그림은 받지 않은 상태다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class EventScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = EventListUiState(isLoading = false, events = sampleEvents, today = LocalDate.of(2026, 11, 8))

    @Test
    fun listLight() = compose.captureRunkkScreen("event_list_light", isDarkTheme = false) {
        EventListScreen(state, onBack = {}, onTab = {}, onEvent = {}, onRetry = {})
    }

    @Test
    fun listDark() = compose.captureRunkkScreen("event_list_dark") {
        EventListScreen(state, onBack = {}, onTab = {}, onEvent = {}, onRetry = {})
    }

    @Test
    fun listPast() = compose.captureRunkkScreen("event_list_past", isDarkTheme = false) {
        EventListScreen(state.copy(tab = EventTab.PAST), onBack = {}, onTab = {}, onEvent = {}, onRetry = {})
    }

    @Test
    fun inboxWithEvents() = compose.captureRunkkScreen("notification_inbox_event", isDarkTheme = false) {
        val clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"))
        InboxScreen(
            InboxUiState(
                isLoading = false,
                groups = groupInbox(sampleItems, clock),
                now = clock.instant(),
                zone = clock.zone,
                eventSummary = InboxEventSummary(ongoingCount = 2, hasUnseen = true)
            ),
            onBack = {},
            onItem = {},
            onRetry = {}
        )
    }

    /** 알림함 운영자 공지 행(피그마 RunKK / 알림함 / 공지 행 62268:2807): 링크 있는 안 읽은 공지와 링크 없는 읽은 공지. */
    @Test
    fun inboxWithNotice() = compose.captureRunkkScreen("notification_inbox_notice", isDarkTheme = false) {
        val clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"))
        val notices = listOf(
            Inbox.Item(
                21,
                NOTICE_TYPE,
                "코치님 이벤트 당첨",
                "구글 폼에 연락처를 남겨 주세요",
                clock.instant().minusSeconds(60),
                isRead = false,
                screen = LINK_SCREEN,
                targetId = "https://forms.gle/AbCdEf123"
            ),
            Inbox.Item(22, NOTICE_TYPE, "안내", "점검이 끝났어요", clock.instant().minusSeconds(7_200), true, null)
        )
        InboxScreen(
            InboxUiState(
                isLoading = false,
                groups = groupInbox(notices + sampleItems, clock),
                now = clock.instant(),
                zone = clock.zone,
                eventSummary = InboxEventSummary(ongoingCount = 2, hasUnseen = false)
            ),
            onBack = {},
            onItem = {},
            onRetry = {}
        )
    }
}
