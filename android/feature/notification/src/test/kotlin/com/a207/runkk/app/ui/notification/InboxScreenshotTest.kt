package com.a207.runkk.app.ui.notification

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 알림함 스크린샷(가짜 UiState, 390×844dp 다크). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class InboxScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"))

    @Test
    fun inbox() = compose.captureRunkkScreen("notification_inbox") {
        InboxScreen(
            InboxUiState(
                isLoading = false,
                groups = groupInbox(sampleItems, clock),
                now = clock.instant(),
                zone = clock.zone
            ),
            onBack = {
            },
            onItem = {},
            onRetry = {}
        )
    }

    @Test
    fun empty() = compose.captureRunkkScreen("notification_inbox_empty") {
        InboxScreen(InboxUiState(isLoading = false), onBack = {}, onItem = {}, onRetry = {})
    }
}
