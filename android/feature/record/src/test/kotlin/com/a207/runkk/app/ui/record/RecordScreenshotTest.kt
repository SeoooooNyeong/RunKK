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

    private val today = java.time.LocalDate.of(2026, 10, 20)

    private fun listState(view: RecordView) = RecordListUiState(
        month = YearMonth.of(2026, 10),
        today = today,
        zone = zone,
        view = view,
        isLoading = false,
        runs = runs,
        dailyDistance = dailyValidDistance(runs, zone),
        summary = RecordListUiState.MonthSummary(11, Distance(54_800), streakDays = 7, streakWeeks = 2),
        selectedDate = java.time.LocalDate.of(2026, 10, 20),
        recentRuns = runs,
        pendingCount = 1
    )

    @Test
    fun calendar() = compose.captureRunkkScreen("G7_record_calendar") {
        RecordListScreen(listState(RecordView.CALENDAR), RecordListActions())
    }

    @Test
    fun calendarLight() = compose.captureRunkkScreen("G7_record_calendar_light", isDarkTheme = false) {
        RecordListScreen(listState(RecordView.CALENDAR), RecordListActions())
    }

    @Test
    fun list() = compose.captureRunkkScreen("G8_record_list") {
        RecordListScreen(listState(RecordView.LIST), RecordListActions())
    }

    @Test
    fun listLight() = compose.captureRunkkScreen("G8_record_list_light", isDarkTheme = false) {
        RecordListScreen(listState(RecordView.LIST), RecordListActions())
    }

    @Test
    fun emptyMonth() = compose.captureRunkkScreen("G7_record_empty") {
        RecordListScreen(
            RecordListUiState(month = YearMonth.of(2026, 9), today = today, zone = zone, isLoading = false),
            RecordListActions()
        )
    }

    @Test
    fun recordDetail() = compose.captureRunkkScreen("G9_record_detail") {
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
    fun cardFront() = compose.captureRunkkScreen("G1_record_card_front") {
        val card = runCard().copy(
            averageHeartRate = 152,
            newRecords = listOf("최장 거리 신기록"),
            weather = com.a207.runkk.app.domain.record.CardWeather(
                14,
                com.a207.runkk.app.domain.record.CardWeather.Sky.CLEAR
            ),
            ownerName = "달리는곰"
        )
        val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
        RecordCardScreen(
            RecordCardUiState(
                isLoading = false,
                card = card,
                front = renderer.render(card, CardSide.FRONT, null, null),
                back = renderer.render(card, CardSide.BACK, null, null)
            ),
            onClose = {},
            onSideSelected = {},
            onShare = {},
            onMessageShown = {},
            onRetry = {}
        )
    }

    /** B-7 1차 배포 카드 팝업: 앞면만, 공유·뒤집기 없음. */
    @Test
    fun cardFrontFirstRelease() =
        compose.captureRunkkScreen("B7_record_card_front_first_release", isDarkTheme = false) {
            val card = runCard().copy(
                averageHeartRate = 152,
                newRecords = listOf("최장 거리 신기록"),
                weather = com.a207.runkk.app.domain.record.CardWeather(
                    14,
                    com.a207.runkk.app.domain.record.CardWeather.Sky.CLEAR
                ),
                ownerName = "달리는곰"
            )
            val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
            androidx.compose.runtime.CompositionLocalProvider(
                com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
                    com.a207.runkk.app.core.designsystem.ReleaseScope.FIRST_RELEASE
            ) {
                RecordCardScreen(
                    RecordCardUiState(
                        isLoading = false,
                        card = card,
                        front = renderer.render(card, CardSide.FRONT, null, null),
                        back = renderer.render(card, CardSide.BACK, null, null)
                    ),
                    onClose = {},
                    onSideSelected = {},
                    onShare = {},
                    onMessageShown = {},
                    onRetry = {}
                )
            }
        }

    @Test
    fun cardBack() = compose.captureRunkkScreen("G2_record_card_back") {
        val card = runCard()
        val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
        RecordCardScreen(
            RecordCardUiState(
                isLoading = false,
                card = card,
                front = renderer.render(card, CardSide.FRONT, null, null),
                back = renderer.render(card, CardSide.BACK, null, null),
                side = CardSide.BACK
            ),
            onClose = {},
            onSideSelected = {},
            onShare = {},
            onMessageShown = {},
            onRetry = {}
        )
    }

    /** G3 카드 공유: 코스 지도 공유가 꺼진 기록이라 "코스 지도 표시"가 잠겨 있다. */
    @Test
    fun cardShareCourseOff() = compose.captureRunkkScreen("G3_card_share_course_off") {
        val card = runCard(shared = false)
        val renderer = RunCardRenderer(RuntimeEnvironment.getApplication())
        RecordCardScreen(
            RecordCardUiState(
                isLoading = false,
                card = card,
                front = renderer.render(card, CardSide.FRONT, null, null),
                back = renderer.render(card, CardSide.BACK, null, null),
                isShareStep = true
            ),
            onClose = {},
            onSideSelected = {},
            onShare = {},
            onMessageShown = {},
            onRetry = {}
        )
    }

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun recordDetailLight() = compose.captureRunkkScreen("G9_record_detail_light", isDarkTheme = false) {
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
}
