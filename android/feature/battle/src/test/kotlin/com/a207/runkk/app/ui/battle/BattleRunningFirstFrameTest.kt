package com.a207.runkk.app.ui.battle

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.TRACK_WARMUP_TAG
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 진행 화면 첫 판(S15P31A207-335 8회차): 화면이 붙는 첫 판부터 조정자의 카운트다운(제목·숫자)을 그린다. 경로 인자 반영을 첫 구성 뒤
 * 효과로 미루면 첫 판은 빈 「곧 출발해요」이고 숫자는 다음 판에야 보였다(에뮬레이터 녹화에서 한 판 0.4초).
 */
@RunWith(RobolectricTestRunner::class)
class BattleRunningFirstFrameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `첫 판부터 셀프런 카운트다운 제목과 숫자 3을 그린다`() {
        val scheduler = TestCoroutineScheduler()
        val w = BattleWorld(CoroutineScope(StandardTestDispatcher(scheduler)))
        w.coordinator.onRecordBattleStart(recordStart(Distance(1_000), CardKind.SELF_RUN, "과거의 나"))
        scheduler.runCurrent()
        val vm = BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts)
        // 다음 판을 그리지 않는다: 첫 구성 결과만 본다
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningRoute(Routes.BattleRunning(42, "과거의 나", 1_000, OpponentKind.SELF.name), vm)
            }
        }
        compose.onNodeWithText("과거의 나와 대결해요").assertExists()
        compose.onNodeWithText("3", useUnmergedTree = true).assertExists()
        // 트랙 아바타 데우기는 첫 판을 무겁게 하지 않게 다음 판에 붙인다
        compose.onNodeWithTag(TRACK_WARMUP_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(TRACK_WARMUP_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `S15P31A207-335 누르자마자 연 카운트다운은 응답 전 첫 판부터 3을 그린다`() {
        val scheduler = TestCoroutineScheduler()
        val w = BattleWorld(CoroutineScope(StandardTestDispatcher(scheduler)))
        w.battles.startGate = CompletableDeferred()
        val ticket = w.coordinator.startRecordBattle(Distance(1_000)) { w.battles.startSelfBattle(Distance(1_000)) }
        val vm = BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningRoute(Routes.BattleRunning(ticket, "나", 1_000, OpponentKind.SELF.name), vm)
            }
        }
        compose.onNodeWithText("과거의 나와 대결해요").assertExists()
        compose.onNodeWithText("3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `S15P31A207-335 대결 만들기가 실패하면 숫자 대신 오류와 다시 시도를 보이고 누르면 3부터 다시 센다`() {
        val scheduler = TestCoroutineScheduler()
        val w = BattleWorld(CoroutineScope(StandardTestDispatcher(scheduler)))
        w.battles.startResult = Outcome.Failure(AppError.Rejected("ALREADY_IN_BATTLE"))
        val ticket = w.coordinator.startRecordBattle(Distance(1_000)) { w.battles.startSelfBattle(Distance(1_000)) }
        scheduler.runCurrent()
        val vm = BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningRoute(Routes.BattleRunning(ticket, "나", 1_000, OpponentKind.SELF.name), vm)
            }
        }
        compose.onNodeWithText("이미 매칭을 기다리거나 대결 중이에요").assertExists()
        compose.onNodeWithText("3", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("다시 고르기").assertExists()

        w.battles.startResult = null
        compose.onNodeWithText("다시 시도").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("3", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("이미 매칭을 기다리거나 대결 중이에요").assertDoesNotExist()
    }
}
