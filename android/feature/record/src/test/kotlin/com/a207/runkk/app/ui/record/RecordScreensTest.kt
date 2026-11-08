package com.a207.runkk.app.ui.record

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.record.NewRecordMark
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordVerdict
import com.a207.runkk.app.domain.record.RunSummary
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Seoul")

    private val today = LocalDate.of(2026, 10, 20)

    private fun listState(view: RecordView, runs: List<RunSummary>) = RecordListUiState(
        month = YearMonth.of(2026, 10),
        today = today,
        zone = zone,
        view = view,
        isLoading = false,
        runs = runs,
        dailyDistance = dailyValidDistance(runs, zone),
        summary = RecordListUiState.MonthSummary(12, Distance(61_300), 4),
        selectedDate = today,
        recentRuns = runs
    )

    @Test
    fun `TC-9731 TC-9730 달력은 월 요약·달린 날 km를 보이고 날짜를 누르면 그날 줄, 줄을 누르면 상세로 간다`() {
        val opened = mutableListOf<String>()
        val picked = mutableListOf<LocalDate>()
        val runs = listOf(summary("r3", 20, 5_020), summary("r1", 14, 2_000, isValid = false))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordListScreen(
                    listState(RecordView.CALENDAR, runs),
                    RecordListActions(onRun = { opened += it }, onDateSelected = { picked += it })
                )
            }
        }
        compose.onNodeWithText("기록").assertExists()
        compose.onNodeWithText("달력").assertIsSelected()
        compose.onNodeWithText("12번").assertExists()
        compose.onNodeWithText("61.3km").assertExists()
        compose.onNodeWithText("4일째").assertExists()
        compose.onNodeWithContentDescription("20일 5.0킬로미터", substring = true).assertExists()
        // 집계 제외 기록만 있는 날(14일)도 칸에 표시가 없다(TC-9730).
        compose.onNodeWithContentDescription("14일", substring = true).performClick()
        assertEquals(listOf(LocalDate.of(2026, 10, 14)), picked)
        compose.onNodeWithText("집계 제외", substring = true).assertDoesNotExist()
        compose.onNodeWithText("10월 20일 (화) · 오전 7:00").performScrollTo().performClick()
        assertEquals(listOf("r3"), opened)
    }

    @Test
    fun `TC-9732 TC-9733 목록은 이번 주·이전으로 묶고 행에 종류·승패·신기록 태그를 붙인다`() {
        val runs = listOf(
            summary("free", 20, 5_210).copy(
                isNewRecord = true,
                newRecords = listOf(NewRecordMark(NewRecordMark.Kind.LONGEST, null))
            ),
            summary("win", 19, 2_170, kind = CardKind.BATTLE_RUN)
                .copy(battleType = RecordBattleType.RANDOM, verdict = RecordVerdict.WIN),
            summary("friend", 11, 3_000, kind = CardKind.BATTLE_RUN)
                .copy(battleType = RecordBattleType.FRIEND, verdict = RecordVerdict.LOSE),
            summary("pending", 10, 3_000, kind = CardKind.BATTLE_RUN)
                .copy(battleType = RecordBattleType.RANDOM, isPending = true),
            summary("self", 9, 3_000, kind = CardKind.SELF_RUN).copy(battleType = RecordBattleType.SELF),
            summary("excluded", 8, 840, isValid = false)
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordListScreen(listState(RecordView.LIST, runs), RecordListActions())
            }
        }
        compose.onNodeWithText("이번 주").assertExists()
        compose.onNodeWithText("최장 거리").assertExists()
        compose.onNodeWithText("배틀런 · 승리").assertExists()
        compose.onNodeWithText("이전").performScrollTo()
        compose.onNodeWithText("친구 대결 · 패배").performScrollTo().assertExists()
        compose.onNodeWithText("배틀런").performScrollTo().assertExists()
        compose.onNodeWithText("셀프런").performScrollTo().assertExists()
        compose.onNodeWithText("자유런").assertDoesNotExist()
        // 집계 제외 기록은 행에 "집계 제외"를 붙인다(D29 번복, 2026-10-30)
        compose.onNodeWithText("집계 제외").performScrollTo().assertExists()
        compose.onNodeWithText("10/20 화 · 28:49 · 5'32\"").assertExists()
    }

    @Test
    fun `D101 기록 제목은 바 가운데에 있다(G-7, G-8, G-10)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordListScreen(listState(RecordView.LIST, emptyList()), RecordListActions())
            }
        }
        assertTitleCentered("기록")
    }

    @Test
    fun `D101 기록 상세 제목은 바 가운데에 있다(G-9)`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RecordDetailScreen(
                        RecordDetailUiState(isLoading = false, recordId = "r1", detail = detail()),
                        onBack = {},
                        onShare = {},
                        onRetry = {},
                        deleteActions = RecordDeleteActions(onDelete = {}, onConfirm = {}, onDismiss = {})
                    )
                }
            }
        }
        assertTitleCentered("기록 상세")
    }

    @Test
    fun `D101 카드 공유 제목은 바 가운데에 있다(B-8)`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordCardScreen(
                    RecordCardUiState(
                        isLoading = false,
                        card = runCard(shared = true),
                        front = bitmap,
                        isShareStep = true
                    ),
                    onClose = {},
                    onSideSelected = {},
                    onShare = {},
                    onMessageShown = {},
                    onRetry = {}
                )
            }
        }
        assertTitleCentered("카드 공유")
    }

    /** [title] 제목(heading)의 가로 가운데가 화면 가운데에 있는지 본다(D101). */
    private fun assertTitleCentered(title: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bar = compose.onNode(hasText(title) and isHeading()).getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value / 2f, (bar.left + bar.right).value / 2f, 1f, "$title 제목 가운데")
    }

    @Test
    fun `대결 탭은 칸에 넣은 그림(ui·battle 대결 이력 탭)을 그린다(D-281)`() {
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RECORD_BATTLES to listOf({ Text("대결 이력 칸") }))) {
                RunkkTheme(reduceMotion = true) {
                    RecordListScreen(listState(RecordView.BATTLE, emptyList()), RecordListActions())
                }
            }
        }
        compose.onNodeWithText("대결 이력 칸").assertExists()
    }

    @Test
    fun `TC-9735 TC-9730 기록 상세는 유효 기록에 배지 없이 카드 공유를 보이고, 삭제할 수 있는 기록에만 기록 삭제와 확인 대화상자를 보인다`() {
        var state by mutableStateOf(
            RecordDetailUiState(isLoading = false, recordId = "r1", detail = detail().copy(isDeletable = true))
        )
        val calls = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RecordDetailScreen(
                        state,
                        onBack = {},
                        onShare = { calls += "share" },
                        onRetry = {},
                        deleteActions = RecordDeleteActions(
                            onDelete = { calls += "delete" },
                            onConfirm = { calls += "confirm" },
                            onDismiss = { calls += "dismiss" }
                        )
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("코스 지도").assertExists()
        compose.onNodeWithText("유효 러닝").assertDoesNotExist()
        compose.onNodeWithText("집계 제외").assertDoesNotExist()
        // 1차 배포에서도 기록 카드 앞면 공유는 연다(D93).
        compose.onNodeWithText("카드 공유").performClick()
        assertEquals(listOf("share"), calls)
        calls.clear()
        compose.onNodeWithText("기록 삭제").performScrollTo().performClick()
        assertEquals(listOf("delete"), calls)
        state = state.copy(isConfirmingDelete = true)
        compose.onNodeWithText("기록을 삭제할까요?").assertExists()
        compose.onNodeWithText("삭제한 기록은 되돌릴 수 없어요.").assertExists()
        compose.onNodeWithText("삭제").performClick()
        assertEquals(listOf("delete", "confirm"), calls)
        state = state.copy(isConfirmingDelete = false, detail = state.detail!!.copy(isDeletable = false))
        compose.onNodeWithText("기록 삭제").assertDoesNotExist()
    }

    @Test
    fun `기록 상세는 카드 공유가 열려 있을 때만 카드 공유를 보인다(2차 배포)`() {
        var shared = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(isLoading = false, recordId = "r1", detail = detail()),
                    onBack = {},
                    onShare = { shared++ },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("카드 공유").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun `기록 상세는 날씨·정지 구간·소모 열량을 보인다(G-9)`() {
        val withWeather = detail().copy(
            weather = null,
            temperatureC = 12,
            sky = "CLEAR",
            pauseCount = 2,
            paused = Duration.ofSeconds(185)
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(isLoading = false, recordId = "r1", detail = withWeather),
                    onBack = {},
                    onShare = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("12°C 맑음", substring = true).assertExists()
        compose.onNodeWithText("정지 구간 2회, 3분").performScrollTo().assertExists()
        compose.onNodeWithText("소모 열량").performScrollTo().assertExists()
        compose.onNodeWithText("전체 공개").assertDoesNotExist()
    }

    @Test
    fun `기록 상세는 본인 기록의 공개 범위를 칩으로 보인다(서버 recordVisibility)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordDetailScreen(
                    RecordDetailUiState(
                        isLoading = false,
                        recordId = "r1",
                        detail = detail().copy(recordVisibility = "CREW")
                    ),
                    onBack = {},
                    onShare = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("크루 공개").assertExists()
    }

    @Test
    fun `TC-9734 기록 상세는 구간 페이스·최고 1km(같으면 앞 구간)·평균·최고 심박과 불안정 구간 수를 보인다`() {
        val withSplits = detail().copy(
            splitPaces = listOf(Pace(370), Pace(318), Pace(318), Pace(360)),
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
        // 공유는 그려진 앞면을 찍어 보낸다(D113). 시험 환경은 누르기 전에 한 번 그려 둔다.
        compose.onRoot().captureToImage()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.onNodeWithText("더 보기").performClick()
        assertEquals(listOf(CardShareKind.SAVE, CardShareKind.SYSTEM), shares)
    }

    @Test
    fun `카드 공유 단계는 보이는 카드를 찍어 넘기고, 잠긴 코스 지도 표시를 누르면 안내를 부른다(K-2)`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val captured = mutableListOf<com.a207.runkk.app.ui.share.CapturedCard?>()
        var locked = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RecordCardScreen(
                    RecordCardUiState(
                        isLoading = false,
                        card = runCard(shared = false),
                        front = bitmap,
                        back = bitmap,
                        isShareStep = true
                    ),
                    onClose = {},
                    onSideSelected = {},
                    onShare = {},
                    onMessageShown = {},
                    onRetry = {},
                    onShareCaptured = { _, card -> captured += card },
                    onCourseLocked = { locked++ }
                )
            }
        }
        compose.onNodeWithText("인스타그램 스토리").assertExists()
        compose.onNodeWithText("코스 지도 표시").performClick()
        assertEquals(1, locked)
        compose.onRoot().captureToImage()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.waitForIdle()
        val card = assertNotNull(captured.single())
        assertTrue(card.bitmap.width > 0 && card.bitmap.height > card.bitmap.width, "세로 카드 ${card.bitmap.width}")
    }

    @Test
    fun `B-7 카드 앞면은 날짜·날씨·거리·신기록·세 지표이고 서명 줄(D113)과 종류 라벨이 없다(FR-068)`() {
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
        compose.onNodeWithText("달리는곰, 런크크", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("자유런", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TC-9699 D93 1차 배포에서는 앞면 공유가 열리고 뒤집기와 면 점은 없다`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val sides = mutableListOf<com.a207.runkk.app.ui.share.CardSide>()
        val steps = mutableListOf<Boolean>()
        var closed = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                    RecordCardScreen(
                        RecordCardUiState(isLoading = false, card = runCard(), front = bitmap, back = bitmap),
                        onClose = { closed++ },
                        onSideSelected = { sides += it },
                        onShare = {},
                        onMessageShown = {},
                        onRetry = {},
                        onShareStep = { steps += it }
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("러닝 카드 앞면", substring = true).performClick()
        compose.onNodeWithTag(RECORD_CARD_DOTS_TAG).assertDoesNotExist()
        compose.onNodeWithText("공유").performClick()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(emptyList(), sides, "뒤집기 없음")
        assertEquals(listOf(true), steps)
        assertEquals(1, closed)
    }

    @Test
    fun `D93 1차 배포 공유 단계는 앞면만 공유하고 코스 지도 표시가 없다`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val kinds = mutableListOf<CardShareKind>()
        var scope by mutableStateOf(ReleaseScope.FIRST_RELEASE)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalReleaseScope provides scope) {
                    RecordCardScreen(
                        RecordCardUiState(
                            isLoading = false,
                            card = runCard().copy(isCourseMapShared = true),
                            front = bitmap,
                            back = bitmap,
                            isShareStep = true
                        ),
                        onClose = {},
                        onSideSelected = {},
                        onShare = { kinds += it },
                        onMessageShown = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("코스 지도 표시").assertDoesNotExist()
        // 공유는 그려진 앞면을 찍어 보낸다(D113). 시험 환경은 누르기 전에 한 번 그려 둔다.
        compose.onRoot().captureToImage()
        compose.onNodeWithText("더 보기").performClick()
        assertEquals(listOf(CardShareKind.SYSTEM), kinds)
        scope = ReleaseScope.ALL_OPEN
        compose.onNodeWithText("코스 지도 표시").assertExists()
    }

    @Test
    fun `R2-18 D93 카드 팝업은 배경 장면 위에 있고 카드 바로 아래 공유와 닫기가 반씩 나란하다(B-7)`() {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 10, android.graphics.Bitmap.Config.ARGB_8888)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                    RecordCardScreen(
                        RecordCardUiState(isLoading = false, card = runCard(), front = bitmap, back = bitmap),
                        onClose = {},
                        onSideSelected = {},
                        onShare = {},
                        onMessageShown = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithTag(RECORD_CARD_BACKDROP_TAG).assertExists()
        val card = compose.onNodeWithContentDescription("러닝 카드 앞면", substring = true).getBoundsInRoot()
        val close = compose.onNodeWithText("닫기").getBoundsInRoot()
        val share = compose.onNodeWithText("공유").getBoundsInRoot()
        val gap = close.top - card.bottom
        assertTrue(gap >= 0.dp && gap <= 40.dp, "카드와 닫기 사이 $gap")
        assertEquals(share.top, close.top, "같은 줄")
        assertTrue(share.right <= close.left, "공유가 왼쪽")
        val shareWidth = share.right - share.left
        val closeWidth = close.right - close.left
        assertTrue((shareWidth - closeWidth).value in -1f..1f, "반씩 $shareWidth $closeWidth")
    }
}
