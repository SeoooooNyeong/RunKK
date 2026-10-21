package com.a207.runkk.app.ui.record

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunCardRenderer
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 기록 목록·달력(C1)·상세(C2)·카드 팝업 스크린샷(가짜 UiState, 390×844dp 다크). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RecordScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Seoul")
    private val runs = listOf(
        summary("r5", 20, 5_020),
        summary("r4", 17, 5_000, kind = CardKind.BATTLE_RUN),
        summary("r3", 14, 10_200),
        summary("r2", 12, 4_100, kind = CardKind.CREW_RUN),
        summary("r1", 8, 2_000, isValid = false)
    )

    private fun listState(view: RecordView) = RecordListUiState(
        month = YearMonth.of(2026, 10),
        view = view,
        isLoading = false,
        runs = runs,
        dailyDistance = runs.groupBy {
            it.startedAt.atZone(zone).toLocalDate()
        }.mapValues { (_, l) -> l.fold(Distance.ZERO) { a, r -> a.plus(r.distance) } },
        pendingCount = 1,
        zone = zone
    )

    @Test
    fun calendar() = compose.captureRunkkScreen("C1_record_calendar") {
        RecordListScreen(listState(RecordView.CALENDAR), onBack = {
        }, onViewSelected = {}, onPreviousMonth = {}, onNextMonth = {}, onRun = {}, onRetry = {})
    }

    @Test
    fun list() = compose.captureRunkkScreen("C1_record_list") {
        RecordListScreen(listState(RecordView.LIST), onBack = {
        }, onViewSelected = {}, onPreviousMonth = {}, onNextMonth = {}, onRun = {}, onRetry = {})
    }

    @Test
    fun emptyMonth() = compose.captureRunkkScreen("C1_record_empty") {
        RecordListScreen(RecordListUiState(month = YearMonth.of(2026, 9), isLoading = false, zone = zone), {
        }, {}, {}, {}, {}, {})
    }

    @Test
    fun recordDetail() = compose.captureRunkkScreen("C2_record_detail") {
        val base = detail()
        RecordDetailScreen(
            RecordDetailUiState(
                isLoading = false,
                recordId = "r1",
                detail = base.copy(
                    route = listOf(
                        GeoPoint(37.50, 127.00),
                        GeoPoint(37.506, 127.008),
                        GeoPoint(37.502, 127.016),
                        GeoPoint(37.51, 127.024),
                        GeoPoint(37.512, 127.03)
                    ),
                    unstableSegments = listOf(
                        RecordDetail.Unstable(Duration.ofSeconds(900), Duration.ofSeconds(1_200), Distance(900))
                    ),
                    splitPaces = listOf(Pace(341), Pace(335), Pace(318), Pace(330), Pace(336)),
                    fastestKm = Pace(318),
                    averageHeartRate = 152,
                    maxHeartRate = 178,
                    heartRateZones = mapOf(
                        "Z1" to Duration.ofMinutes(2),
                        "Z2" to Duration.ofMinutes(8),
                        "Z3" to Duration.ofMinutes(12),
                        "Z4" to Duration.ofMinutes(5)
                    ),
                    newRecords = listOf("최장 거리 신기록 · 직전 4.80km")
                )
            ),
            onBack = {},
            onShare = {},
            onRetry = {}
        )
    }

    @Test
    fun cardFront() = compose.captureRunkkScreen("C2_record_card_front") {
        val card = runCard()
        val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
        RecordCardScreen(
            RecordCardUiState(
                isLoading = false,
                card = card,
                front = renderer.render(card, CardSide.FRONT),
                back = renderer.render(card, CardSide.BACK)
            ),
            onClose = {},
            onSideSelected = {},
            onShare = {},
            onMessageShown = {},
            onRetry = {}
        )
    }

    @Test
    fun cardBackNotShared() = compose.captureRunkkScreen("C2_record_card_back_not_shared") {
        val card = runCard(shared = false)
        val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
        RecordCardScreen(
            RecordCardUiState(
                isLoading = false,
                card = card,
                front = renderer.render(card, CardSide.FRONT),
                back = renderer.render(card, CardSide.BACK),
                side = CardSide.BACK
            ),
            onClose = {},
            onSideSelected = {},
            onShare = {},
            onMessageShown = {},
            onRetry = {}
        )
    }
}
