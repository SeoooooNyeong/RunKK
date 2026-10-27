package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 대결 화면: 주요 버튼과 접근성 라벨. */
@RunWith(RobolectricTestRunner::class)
class BattleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val candidates = (selfCandidates).map(CandidateUi::of)

    @Test
    fun `나와의 싸움 - 거리를 고르기 전에는 시작할 수 없고, 고르면 시작·이력 버튼`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SelfBattleScreen(
                    RecordBattleUiState(isLoading = false, candidates = candidates),
                    onSelect = { calls += "select:${it.distance.meters}" },
                    onStart = { calls += "start" },
                    onHistory = { calls += "history" },
                    onRetry = {},
                    onMessageShown = {}
                )
            }
        }
        compose.onNodeWithText("거리를 골라 주세요").assertIsNotEnabled()
        compose.onNodeWithContentDescription("3킬로미터, 최고 16분 0초", substring = true).performClick()
        compose.onNodeWithText("함께 달린 기록 ›").performClick()
        compose.onNodeWithText("어떤 거리로 겨뤄 볼까요?").assertExists()
        assertEquals(listOf("select:3000", "history"), calls)
    }

    @Test
    fun `유명인 - 카드 출처 줄은 서버 sourceLabel을 그대로 보여 접두어가 한 번만 붙는다(FR-100)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CelebBattleScreen(
                    RecordBattleUiState(isLoading = false, candidates = celebCandidates.map(CandidateUi::of)),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {}
                )
            }
        }
        // 카드는 보조 기술용 한 문장으로 묶여 있어 그 문장(출처 줄 포함)으로 찾는다.
        compose.onNodeWithContentDescription("2025 서울마라톤", substring = true)
            .assertContentDescriptionEquals("번개발, 10킬로미터, 42분 10초, 출처: 공개 기록 기준 · 2025 서울마라톤")
        compose.onAllNodesWithContentDescription("공개 기록 기준 · 공개 기록 기준", substring = true).assertCountEquals(0)
    }

    @Test
    fun `유명인 - 대결을 누르면 확인 없이 고르고 바로 시작한다(H2)`() {
        val celeb = celebCandidates.map(CandidateUi::of)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CelebBattleScreen(
                    RecordBattleUiState(
                        isLoading = false,
                        candidates = celeb
                    ),
                    onFilter = { calls += "filter:$it" },
                    onPick = { calls += "pick:${it.name}" },
                    onConfirm = { calls += "confirm" },
                    onDismissConfirm = {},
                    onHistory = {},
                    onRetry = {},
                    onMessageShown = {}
                )
            }
        }
        compose.onAllNodesWithText("대결")[0].performClick()
        assertEquals(listOf("pick:번개발", "confirm"), calls)
    }

    @Test
    fun `매칭 대기 - 경과 시간을 읽어 주고 취소 버튼이 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MatchScreen(
                    MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7),
                    onDistanceSelected = {},
                    onStart = {},
                    onCancel = { calls += "cancel" },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithContentDescription("상대를 찾고 있어요, 7초 지남").assertExists()
        compose.onNodeWithText("그만 찾을래요").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `대결 진행 - 그만두기를 누르면 포기 확인을 띄우고, 격차를 읽어 준다(D3, D5)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningScreen(
                    BattleRunningUiState(
                        battleId = 9,
                        opponentName = "바람처럼",
                        phase = BattlePhase.RUNNING,
                        gapMeters = -15,
                        opponentDistance = Distance(2_000)
                    ),
                    onForfeitTap = { calls += "tap" },
                    onForfeitLongPress = { calls += "long" },
                    onForfeitConfirm = {},
                    onForfeitDismiss = {},
                    onClose = {}
                )
            }
        }
        compose.onNodeWithText("그만두기").performClick()
        assertEquals(listOf("long"), calls)
        compose.onNodeWithContentDescription("상대와 거리 차 15미터 뒤처지는 중").assertExists()
    }

    @Test
    fun `대결 진행 - 화면 잠금 버튼과 상대 연결 상태, 잠금 화면에서는 길게 눌러 해제`() {
        var state by mutableStateOf(
            BattleRunningUiState(
                battleId = 9,
                opponentName = "바람처럼",
                phase = BattlePhase.RUNNING,
                gapMeters = 10,
                opponentDistance = Distance(1_000),
                connectionLabel = UiText.Res(R.string.battle_running_checking)
            )
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningScreen(
                    state,
                    onForfeitTap = {},
                    onForfeitLongPress = {},
                    onForfeitConfirm = {},
                    onForfeitDismiss = {},
                    onClose = {},
                    lockActions = BattleLockActions(
                        onLock = { calls += "lock" },
                        onUnlockTap = { calls += "unlockTap" },
                        onUnlockLongPress = { calls += "unlockLong" }
                    )
                )
            }
        }
        compose.onNodeWithText("상대 연결 확인 중")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("배틀런, 5km").assertExists()
        compose.onNodeWithText("잠금").performClick()
        state = state.copy(isLocked = true)
        compose.onNodeWithText("그만두기").assertDoesNotExist()
        val lockedTitle = RuntimeEnvironment.getApplication().getString(R.string.battle_running_locked_pane_title)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, lockedTitle)).assertExists()
        val unlock = compose.onNodeWithContentDescription("화면 잠금 해제")
        unlock.performClick()
        unlock.performTouchInput { longClick() }
        assertEquals(listOf("lock", "unlockTap", "unlockLong"), calls)
    }

    @Test
    fun `함께 달린 기록·셀럽 후보는 필터 결과 건수를 보조 기술에 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    BattleHistoryScreen(
                        BattleHistoryUiState(isLoading = false, history = history()),
                        {},
                        {},
                        {},
                        {},
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    )
                    CelebBattleScreen(
                        RecordBattleUiState(isLoading = false, candidates = celebCandidates.map(CandidateUi::of)),
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    )
                }
            }
        }
        compose.onNodeWithText("${history().total}건")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `대결 결과 - 기록 카드·홈으로·함께 달린 기록`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleResultScreen(
                    BattleResultUiState(isLoading = false, result = battleResult()),
                    onHistory = { calls += "history" },
                    onCard = { calls += "card" },
                    onHome = { calls += "home" },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithContentDescription("함께 달린 기록 보기").performClick()
        compose.onNodeWithText("기록 카드").performClick()
        compose.onNodeWithText("홈으로").performClick()
        compose.onNodeWithContentDescription("기록, 나 27분 30초, 상대 28분 12초").assertExists()
        compose.onNodeWithContentDescription("승. 바람처럼님보다 42초 먼저 도착했어요!").assertExists()
        assertEquals(listOf("history", "card", "home"), calls)
    }

    @Test
    fun `대결 결과 - 본인 기록 ID가 아직 없으면 기록 목록 단추`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleResultScreen(
                    BattleResultUiState(isLoading = false, result = battleResult(recordId = null)),
                    onHistory = {},
                    onCard = { calls += "records" },
                    onHome = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("기록 목록").performClick()
        assertEquals(listOf("records"), calls)
    }
}
