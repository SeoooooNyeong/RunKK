package com.a207.runkk.app.ui.notification

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 이벤트 목록(피그마 62214:1243 Light, 62218:1243 Dark)과 알림함 이벤트 항목(62209:1274) 스크린샷. 그림은 받지 않은 상태다. */
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
}
