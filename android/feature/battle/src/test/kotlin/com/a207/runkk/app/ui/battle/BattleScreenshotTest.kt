package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 대결 화면 스크린샷(가짜 UiState, 390×844dp 다크). v0.14 이름은 피그마 화면 ID(C-1·C-9 배틀런 화면, C-10 초대 대기, C-12 참가 확인,
 * D-2·E-2 카운트다운, D-6 셀프런 결과)이고, 이어 쓰는 화면은 옛 이름(D0~D5)을 그대로 둔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class BattleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val self = selfCandidates.map(CandidateUi::of)
    private val celeb = celebCandidates.map(CandidateUi::of)

    private val cards = listOf(
        BattleModeUiState.DistanceCard(Distance(1_000), Duration.ofSeconds(292), LocalDate.of(2026, 9, 14)),
        BattleModeUiState.DistanceCard(Distance(3_000), Duration.ofSeconds(940), LocalDate.of(2026, 9, 30)),
        BattleModeUiState.DistanceCard(Distance(5_000), Duration.ofSeconds(1_625), LocalDate.of(2026, 10, 12)),
        BattleModeUiState.DistanceCard(Distance(10_000), null, null)
    )

    @Test
    fun battleModeRandom() = compose.captureRunkkScreen("C1_battle_mode_random") {
        BattleModeScreen(BattleModeUiState(cards = cards), BattleModeActions())
    }

    @Test
    fun battleModeFriend() = compose.captureRunkkScreen("C9_battle_mode_friend") {
        BattleModeScreen(
            BattleModeUiState(segment = BattleModeUiState.Segment.FRIEND, cards = cards),
            BattleModeActions()
        )
    }

    @Test
    fun selfBattle() = compose.captureRunkkScreen("D1_self_battle") {
        SelfBattleScreen(RecordBattleUiState(isLoading = false, candidates = self, selected = self[1]), {}, {}, {}, {})
    }

    @Test
    fun celebBattle() = compose.captureRunkkScreen("D1_celeb_battle") {
        CelebBattleScreen(
            RecordBattleUiState(isLoading = false, candidates = celeb, selected = celeb[0]),
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun matchWaiting() = compose.captureRunkkScreen("D2_match_waiting") {
        MatchScreen(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7), MatchActions())
    }

    @Test
    fun matchFoundScreen() = compose.captureRunkkScreen("D2_match_found") {
        MatchScreen(
            MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), startsInSeconds = 7, myTier = "GOLD"),
            MatchActions()
        )
    }

    @Test
    fun matchFoundStuck() = compose.captureRunkkScreen("D2_match_found_forfeit") {
        MatchScreen(MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), canForfeit = true), MatchActions())
    }

    @Test
    fun inviteWaiting() = compose.captureRunkkScreen("C10_invite_waiting") {
        MatchScreen(
            MatchUiState(phase = MatchPhase.INVITING, opponentKind = OpponentKind.FRIEND, waitingSeconds = 7),
            MatchActions()
        )
    }

    @Test
    fun inviteExpired() = compose.captureRunkkScreen("C10_invite_expired") {
        MatchScreen(
            MatchUiState(
                phase = MatchPhase.INVITE_ENDED,
                opponentKind = OpponentKind.FRIEND,
                inviteEnd = MatchUiState.InviteEnd.EXPIRED
            ),
            MatchActions()
        )
    }

    @Test
    fun inviteConfirm() = compose.captureRunkkScreen("C12_invite_confirm") {
        MatchScreen(
            MatchUiState(phase = MatchPhase.CONFIRMING, opponentKind = OpponentKind.FRIEND, invite = inviteView()),
            MatchActions()
        )
    }

    @Test
    fun inviteEndedNotice() = compose.captureRunkkScreen("C12_invite_ended_notice") {
        MatchScreen(
            MatchUiState(
                phase = MatchPhase.CONFIRMING,
                opponentKind = OpponentKind.FRIEND,
                notice = UiText.Res(R.string.battle_join_ended)
            ),
            MatchActions()
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

    /** 북동쪽으로 휘는 짧은 경로(대결 진행). */
    private val route = RouteLine(
        listOf(
            GeoPoint(37.5000, 127.0000),
            GeoPoint(37.5006, 127.0004),
            GeoPoint(37.5012, 127.0012),
            GeoPoint(37.5016, 127.0024),
            GeoPoint(37.5024, 127.0031)
        )
    )

    private val running = BattleRunningUiState(
        battleId = 9,
        opponentName = "바람처럼",
        phase = BattlePhase.RUNNING,
        gapMeters = -42,
        opponentDistance = Distance(2_800),
        myDistance = Distance(2_842),
        elapsed = Duration.ofSeconds(940),
        currentPace = Pace(324),
        averagePace = Pace(331),
        connectionLabel = UiText.Res(R.string.battle_running_connected),
        route = route
    )

    @Test
    fun battleRunning() = compose.captureRunkkScreen("D3_battle_running") {
        BattleRunningScreen(running, BattleRunningActions())
    }

    @Test
    fun battleRunningOvertook() = compose.captureRunkkScreen("D3_battle_running_overtook") {
        BattleRunningScreen(
            running.copy(gapMeters = 42, guidance = BattleGuidance.Kind.OVERTOOK, overtakes = 1),
            BattleRunningActions()
        )
    }

    @Test
    fun battleRunningDisconnected() = compose.captureRunkkScreen("D3_battle_running_disconnected") {
        BattleRunningScreen(running.copy(isLiveLost = true), BattleRunningActions())
    }

    @Test
    fun battleCountdown() = compose.captureRunkkScreen("D3_battle_countdown") {
        BattleRunningScreen(
            BattleRunningUiState(
                opponentName = "10월 2일의 나",
                kind = OpponentKind.SELF,
                phase = BattlePhase.COUNTDOWN,
                countdown = 2,
                opponentRecord = Duration.ofSeconds(1_812),
                opponentRecordedOn = LocalDate.of(2026, 10, 2)
            ),
            BattleRunningActions()
        )
    }

    @Test
    fun celebCountdown() = compose.captureRunkkScreen("E2_celeb_countdown") {
        BattleRunningScreen(
            BattleRunningUiState(
                opponentName = "김선교 대공님",
                kind = OpponentKind.CELEBRITY,
                distance = Distance(10_000),
                phase = BattlePhase.COUNTDOWN,
                countdown = 2,
                opponentRecord = Duration.ofSeconds(3_520),
                myBest = Duration.ofSeconds(3_730)
            ),
            BattleRunningActions()
        )
    }

    @Test
    fun selfRunning() = compose.captureRunkkScreen("D3_self_running") {
        BattleRunningScreen(
            running.copy(
                kind = OpponentKind.SELF,
                gapMeters = 42,
                connectionLabel = null,
                opponentRecordedOn = LocalDate.of(2026, 10, 2)
            ),
            BattleRunningActions()
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
                connectionLabel = null,
                guidance = BattleGuidance.Kind.OVERTAKEN
            ),
            BattleRunningActions()
        )
    }

    @Test
    fun battleClosing() = compose.captureRunkkScreen("D3_battle_closing_countdown") {
        BattleRunningScreen(
            running.copy(phase = BattlePhase.CLOSING, arrivedName = "바람처럼", closingSeconds = 7),
            BattleRunningActions()
        )
    }

    @Test
    fun forfeitConfirm() = compose.captureRunkkScreen("D3_battle_forfeit_confirm") {
        BattleRunningScreen(running.copy(isConfirmingForfeit = true), BattleRunningActions())
    }

    @Test
    fun selfForfeitConfirm() = compose.captureRunkkScreen("D5_self_forfeit_confirm") {
        BattleRunningScreen(running.copy(kind = OpponentKind.SELF, isConfirmingForfeit = true), BattleRunningActions())
    }

    @Test
    fun resultWin() = compose.captureRunkkScreen("D4_battle_result_win") {
        BattleResultScreen(BattleResultUiState(isLoading = false, result = battleResult()), {}, {}, {})
    }

    @Test
    fun resultDetail() = compose.captureRunkkScreen("D4_battle_result_detail") {
        BattleResultScreen(
            BattleResultUiState(isLoading = false, result = battleResult(), detail = recordDetail()),
            {},
            {},
            {},
            pagerState = rememberPagerState(initialPage = 1) { 2 }
        )
    }

    @Test
    fun selfResult() = compose.captureRunkkScreen("D6_self_result") {
        BattleResultScreen(
            BattleResultUiState(
                isLoading = false,
                result = battleResult().copy(
                    type = BattleHistory.Type.SELF,
                    opponentName = "10월 2일의 나",
                    rows = listOf(
                        BattleResult.Row("기록", "29:58", "30:12"),
                        BattleResult.Row("평균 페이스", "5'59\"", "6'02\"")
                    ),
                    myReach = Duration.ofSeconds(1_798),
                    opponentReach = Duration.ofSeconds(1_812)
                )
            ),
            {},
            {},
            {}
        )
    }

    @Test
    fun celebResultLose() = compose.captureRunkkScreen("E6_celeb_result_lose") {
        BattleResultScreen(
            BattleResultUiState(
                isLoading = false,
                result = battleResult(verdict = "LOSE").copy(
                    type = BattleHistory.Type.CELEBRITY,
                    opponentName = "김선교 대공",
                    myReach = Duration.ofSeconds(3_640),
                    opponentReach = Duration.ofSeconds(3_500)
                )
            ),
            {},
            {},
            {}
        )
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
            {}
        )
    }

    @Test
    fun resultPending() = compose.captureRunkkScreen("D4_battle_result_pending") {
        BattleResultScreen(
            BattleResultUiState(isLoading = false, result = battleResult(pending = true, verdict = null)),
            {},
            {},
            {}
        )
    }

    @Test
    fun historyTab() = compose.captureRunkkScreen("G10_record_battles_tab") {
        BattleHistoryTabContent(BattleHistoryUiState(isLoading = false, history = history()), {}, {}, {})
    }

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun battleRunningLight() = compose.captureRunkkScreen("D3_battle_running_light", isDarkTheme = false) {
        BattleRunningScreen(running, BattleRunningActions())
    }

    @Test
    fun resultWinLight() = compose.captureRunkkScreen("D4_battle_result_win_light", isDarkTheme = false) {
        BattleResultScreen(BattleResultUiState(isLoading = false, result = battleResult()), {}, {}, {})
    }

    @Test
    fun resultDetailLight() = compose.captureRunkkScreen("D4_battle_result_detail_light", isDarkTheme = false) {
        FirstRelease {
            BattleResultScreen(
                BattleResultUiState(isLoading = false, result = battleResult(), detail = recordDetail()),
                {},
                {},
                {},
                pagerState = rememberPagerState(initialPage = 1) { 2 }
            )
        }
    }

    @Test
    fun matchWaitingLight() = compose.captureRunkkScreen("D2_match_waiting_first_release_light", isDarkTheme = false) {
        FirstRelease { MatchScreen(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7), MatchActions()) }
    }

    @Test
    fun matchFoundLight() = compose.captureRunkkScreen("D2_match_found_first_release_light", isDarkTheme = false) {
        FirstRelease {
            MatchScreen(
                MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), startsInSeconds = 7, myTier = "GOLD"),
                MatchActions()
            )
        }
    }
}

/** 1차 배포 범위(아바타 꾸미기 닫힘): 기본 아바타 그림이 보인다. */
@androidx.compose.runtime.Composable
private fun FirstRelease(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
            com.a207.runkk.app.core.designsystem.ReleaseScope.FIRST_RELEASE,
        content = content
    )
}
