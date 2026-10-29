package com.a207.runkk.app.ui.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RecordDetail
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `달력은 달린 날을 km로 읽어 주고 기록을 누르면 상세로 간다`() {
        val opened = mutableListOf<String>()
        val runs = listOf(summary("r3", 20, 5_020), summary("r1", 14, 2_000, isValid = false))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordListScreen(
                    state = RecordListUiState(
                        month = YearMonth.of(2026, 10),
                        isLoading = false,
                        runs = runs,
                        dailyDistance = mapOf(LocalDate.of(2026, 10, 20) to Distance(5_020)),
                        zone = zone
                    ),
                    onBack = {},
                    onViewSelected = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onRun = { opened += it },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithContentDescription("20일 5.0킬로미터").assertExists()
        compose.onNodeWithContentDescription("이전 달").assertExists()
        compose.onNodeWithText("달력").assertIsSelected()
        compose.onNodeWithText("집계 제외").performScrollTo()
        compose.onNodeWithText("2026년 10월").assertExists()
        compose.onNodeWithText("5.02km, 자유런").performScrollTo().performClick()
        assertEquals(listOf("r3"), opened)
    }

    @Test
    fun `기록 상세는 카드 공유와 코스 지도 라벨을 보인다`() {
        var shared = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(
                        isLoading = false,
                        recordId = "r1",
                        detail = detail()
                    ),
                    onBack = {
                    },
                    onShare = { shared++ },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithContentDescription("코스 지도").assertExists()
        compose.onNodeWithText("유효 러닝").assertExists()
        compose.onNodeWithText("카드 공유").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun `기록 상세는 구간 페이스·최고 1km·평균·최고 심박과 불안정 구간 수를 보인다`() {
        val withSplits = detail().copy(
            splitPaces = listOf(Pace(341), Pace(318), Pace(330)),
            fastestKm = Pace(318),
            averageHeartRate = 152,
            maxHeartRate = 178,
            unstableSegments = listOf(
                RecordDetail.Unstable(Duration.ofSeconds(100), Duration.ofSeconds(160), Distance(180))
            )
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(isLoading = false, recordId = "r1", detail = withSplits),
                    onBack = {},
                    onShare = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("구간 페이스").performScrollTo().assertExists()
        compose.onNodeWithText("가장 빠른 1km: 2km 구간 5:18").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("2킬로미터 구간 1킬로미터당 5분 18초, 가장 빠름").assertExists()
        compose.onNodeWithText("평균 152, 최고 178 bpm").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("코스 지도, 신호 불안정 구간 1곳 점선 표시").assertExists()
    }

    @Test
    fun `기록 상세를 못 읽으면 다시 시도할 수 있다`() {
        var retried = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(
                        isLoading = false,
                        error = UiText.Plain("네트워크에 연결할 수 없어요")
                    ),
                    onBack = {
                    },
                    onShare = {},
                    onRetry = { retried++ }
                )
            }
        }
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `카드 팝업은 눌러 뒤집고 공유로 카드 공유 단계를 연다, 코스 지도 공유가 꺼지면 코스 지도 표시를 잠근다(G1~G3)`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val sides = mutableListOf<com.a207.runkk.app.ui.share.CardSide>()
        val steps = mutableListOf<Boolean>()
        val shares = mutableListOf<CardShareKind>()
        var state by androidx.compose.runtime.mutableStateOf(
            RecordCardUiState(isLoading = false, card = runCard(shared = false), front = bitmap, back = bitmap)
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordCardScreen(
                    state,
                    onClose = {},
                    onSideSelected = { sides += it },
                    onShare = { shares += it },
                    onMessageShown = {},
                    onRetry = {},
                    onShareStep = { steps += it }
                )
            }
        }
        compose.onNodeWithContentDescription("러닝 카드 앞면", substring = true).performClick()
        compose.onNodeWithText("공유").performClick()
        assertEquals(listOf(com.a207.runkk.app.ui.share.CardSide.BACK), sides)
        assertEquals(listOf(true), steps)
        state = state.copy(isShareStep = true)
        compose.onNodeWithText("카드 공유").assertExists()
        compose.onNodeWithText("코스 지도 표시").assertIsNotEnabled()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.onNodeWithText("더 보기").performClick()
        assertEquals(listOf(CardShareKind.SAVE, CardShareKind.SYSTEM), shares)
    }

    @Test
    fun `B-7 카드 앞면은 날짜·날씨·거리·신기록·세 지표·서명이고 종류 라벨이 없다(FR-068)`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val card = runCard().copy(
            averageHeartRate = 152,
            newRecords = listOf("최장 거리 신기록"),
            weather = com.a207.runkk.app.domain.record.CardWeather(
                14,
                com.a207.runkk.app.domain.record.CardWeather.Sky.CLEAR
            ),
            ownerName = "달리는곰"
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
                        com.a207.runkk.app.core.designsystem.ReleaseScope.FIRST_RELEASE
                ) {
                    RecordCardScreen(
                        RecordCardUiState(isLoading = false, card = card, front = bitmap, back = bitmap),
                        onClose = {},
                        onSideSelected = {},
                        onShare = {},
                        onMessageShown = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("맑음 14°", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("신기록", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("운동 시간", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 페이스", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 심박", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("152", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("달리는곰, 런크크", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("자유런", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TC-9699 카드 공유가 닫히면 공유 버튼과 뒤집기가 없고 닫기만 있다`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val sides = mutableListOf<com.a207.runkk.app.ui.share.CardSide>()
        var closed = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
                        com.a207.runkk.app.core.designsystem.ReleaseScope.FIRST_RELEASE
                ) {
                    RecordCardScreen(
                        RecordCardUiState(isLoading = false, card = runCard(), front = bitmap, back = bitmap),
                        onClose = { closed++ },
                        onSideSelected = { sides += it },
                        onShare = {},
                        onMessageShown = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("공유").assertDoesNotExist()
        compose.onNodeWithContentDescription("러닝 카드 앞면", substring = true).performClick()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(emptyList(), sides, "뒤집기 없음")
        assertEquals(1, closed)
    }
}
