package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 대결 화면 스크린샷(가짜 UiState, 390×844dp 다크). 이름은 SRS 5.1 화면 순서(D1~D6). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class BattleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val self = selfCandidates.map(CandidateUi::of)
    private val celeb = celebCandidates.map(CandidateUi::of)

    @Test
    fun selfBattle() = compose.captureRunkkScreen("D1_self_battle") {
        SelfBattleScreen(RecordBattleUiState(isLoading = false, candidates = self, selected = self[1]), {
        }, {}, {}, {}, {})
    }

    @Test
    fun celebBattle() = compose.captureRunkkScreen("D1_celeb_battle") {
        CelebBattleScreen(RecordBattleUiState(isLoading = false, candidates = celeb), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun matchChoosing() = compose.captureRunkkScreen("D2_match_choose_distance") {
        MatchScreen(MatchUiState(), {}, {}, {}, {})
    }

    @Test
    fun matchWaiting() = compose.captureRunkkScreen("D2_match_waiting") {
        MatchScreen(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7), {}, {}, {}, {})
    }

    @Test
    fun matchFoundScreen() = compose.captureRunkkScreen("D2_match_found") {
        MatchScreen(MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), startsInSeconds = 10), {
        }, {}, {}, {})
    }

    @Test
    fun matchFoundStuck() = compose.captureRunkkScreen("D2_match_found_forfeit") {
        MatchScreen(
            MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), canForfeit = true),
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun activeBattle() = compose.captureRunkkScreen("D0_active_battle") {
        Box(Modifier.fillMaxSize().background(RunkkColors.Background))
        ActiveBattleDialog(
            ActiveBattleUiState(
                ActiveBattle(12, BattleHistory.Type.RANDOM, "IN_PROGRESS", Distance(1_000), "단단한치타", null)
            ),
            {},
            {}
        )
    }

    private val running = BattleRunningUiState(
        battleId = 9,
        opponentName = "바람처럼",
        phase = BattlePhase.RUNNING,
        gapMeters = 42,
        opponentDistance = Distance(2_800),
        myDistance = Distance(2_842),
        elapsed = Duration.ofSeconds(940),
        currentPace = Pace(324),
        averagePace = Pace(331),
        connectionLabel = UiText.Res(R.string.battle_running_connected),
        guidance = BattleGuidance.Kind.OVERTOOK,
        overtakes = 2
    )

    @Test
    fun battleRunning() = compose.captureRunkkScreen("D3_battle_running") {
        BattleRunningScreen(running, {}, {}, {}, {}, {})
    }

    @Test
    fun battleRunningDisconnected() = compose.captureRunkkScreen("D3_battle_running_disconnected") {
        BattleRunningScreen(running.copy(isLiveLost = true, guidance = null), {}, {}, {}, {}, {})
    }

    @Test
    fun battleCountdown() = compose.captureRunkkScreen("D3_battle_countdown") {
        BattleRunningScreen(
            BattleRunningUiState(
                opponentName = "과거의 나",
                kind = OpponentKind.SELF,
                phase = BattlePhase.COUNTDOWN,
                countdown = 3
            ),
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun celebRunning() = compose.captureRunkkScreen("D3_battle_running_celeb_sd") {
        BattleRunningScreen(
            running.copy(
                opponentName = "번개발",
                kind = OpponentKind.CELEBRITY,
                distance = Distance(10_000),
                gapMeters = -120,
                opponentDistance = Distance(4_120),
                guidance = BattleGuidance.Kind.OVERTAKEN
            ),
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun battleClosing() = compose.captureRunkkScreen("D3_battle_closing_countdown") {
        BattleRunningScreen(
            running.copy(phase = BattlePhase.CLOSING, arrivedName = "바람처럼", closingSeconds = 7, guidance = null),
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun forfeitConfirm() = compose.captureRunkkScreen("D3_battle_forfeit_confirm") {
        BattleRunningScreen(running.copy(isConfirmingForfeit = true, guidance = null), {}, {}, {}, {}, {})
    }

    @Test
    fun resultWin() = compose.captureRunkkScreen("D4_battle_result_win") {
        BattleResultScreen(BattleResultUiState(isLoading = false, result = battleResult()), {
        }, {}, {}, {})
    }

    @Test
    fun resultWaiting() = compose.captureRunkkScreen("D4_battle_result_waiting") {
        BattleResultScreen(
            BattleResultUiState(
                isLoading = false,
                result = battleResult(pending = true, verdict = null),
                isAwaitingVerdict = true
            ),
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun resultPending() = compose.captureRunkkScreen("D4_battle_result_pending") {
        BattleResultScreen(
            BattleResultUiState(isLoading = false, result = battleResult(pending = true, verdict = null)),
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun historyScreen() = compose.captureRunkkScreen("D5_battle_history") {
        BattleHistoryScreen(BattleHistoryUiState(isLoading = false, history = history()), {}, {}, {}, {})
    }

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun battleRunningLight() = compose.captureRunkkScreen("D3_battle_running_light", isDarkTheme = false) {
        BattleRunningScreen(running, {}, {}, {}, {}, {})
    }

    @Test
    fun resultWinLight() = compose.captureRunkkScreen("D4_battle_result_win_light", isDarkTheme = false) {
        BattleResultScreen(BattleResultUiState(isLoading = false, result = battleResult()), {
        }, {}, {}, {})
    }
}
