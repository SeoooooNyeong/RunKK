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
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.PlacementRoundState as PRS
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 대결 화면 스크린샷(가짜 UiState, 390×844dp 다크). v0.14 이름은 피그마 화면 ID(C-1 랜덤 대결·C-9 친구 대결(v0.18 분리, 라이트), C-10 초대 대기, C-12 참가 확인,
 * D-2·E-2 카운트다운, D-6 셀프런 결과)이고, 이어 쓰는 화면은 옛 이름(D0~D5)을 그대로 둔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class BattleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val self = selfCandidates.map(CandidateUi::of)
    private val celeb = celebCandidates.map(CandidateUi::of)

    private val placementDetail = recordDetail().let { detail ->
        detail.copy(
            summary = detail.summary.copy(
                startedAt = java.time.Instant.parse("2026-10-15T22:12:00Z"),
                distance = Distance(5_210),
                elapsed = Duration.ofSeconds(1_668),
                averagePace = Pace(320)
            ),
            splitPaces = listOf(Pace(331), Pace(326), Pace(318), Pace(322), Pace(312)),
            averageHeartRate = 152,
            heartRateZones = mapOf(
                "Z1" to Duration.ofMinutes(2),
                "Z2" to Duration.ofMinutes(8),
                "Z3" to Duration.ofMinutes(12),
                "Z4" to Duration.ofMinutes(5)
            ),
            paused = Duration.ofSeconds(40)
        )
    }

    private fun placementResultState(state: PRS?, verdict: String, counted: Int, isStillPending: Boolean = false) =
        PlacementResultUiState(
            isLoading = false,
            battleId = 9,
            result = battleResult(
                verdict = verdict
            ).copy(placementRound = 1, placementState = state, totalRewardPoints = 52),
            detail = placementDetail,
            state = state,
            progress = placing(counted, "GOLD".takeIf { counted > 0 }),
            isStillPending = isStillPending
        )

    private fun completeState(tier: String) = PlacementCompleteUiState(
        isLoading = false,
        tier = tier,
        rounds = listOf(
            placementRound(1, 901, "WIN"),
            placementRound(2, 902, "LOSE"),
            placementRound(3, 903, "WIN").copy(distance = Distance(3_000), averagePace = Pace(328))
        ),
        wins = 2,
        losses = 1
    )

    private val friends = listOf(
        RecentFriend(21, "바람처럼", LocalDate.of(2026, 10, 12)),
        RecentFriend(22, "달빛러너", LocalDate.of(2026, 9, 30)),
        RecentFriend(23, "새벽고래", LocalDate.of(2026, 9, 14))
    )

    /** TC-5892 랜덤 대결 v5(61355:2909): 확정 배지 작게, 「?」, 거리 칩, 「내 5km 최고 기록」, 하단 내비 없음. */
    @Test
    fun randomBattle() = compose.captureRunkkScreen("C1_random_battle", isDarkTheme = false) {
        RandomBattleScreen(
            RandomBattleUiState(
                bestByDistance = mapOf(Distance(5_000) to Duration.ofSeconds(1_625)),
                tierDisplay = TierDisplay.Confirmed("GOLD")
            ),
            RandomBattleActions()
        )
    }

    /** TC-5893 친구 대결 닫힘(61312:14822). */
    @Test
    fun friendBattle() = compose.captureRunkkScreen("C9_friend_battle", isDarkTheme = false) {
        FriendBattleScreen(FriendBattleUiState(friends = friends, selectedFriendId = 21), FriendBattleActions())
    }

    /** TC-5894 친구 대결 거리 펼침(61312:14940). */
    @Test
    fun friendBattleOpen() = compose.captureRunkkScreen("C9_friend_battle_open", isDarkTheme = false) {
        FriendBattleScreen(
            FriendBattleUiState(friends = friends, selectedFriendId = 21, isDistanceMenuOpen = true),
            FriendBattleActions()
        )
    }

    /** TC-5895 최근 친구 없음(제안 문구). */
    @Test
    fun friendBattleEmpty() = compose.captureRunkkScreen("C9_friend_battle_empty", isDarkTheme = false) {
        FriendBattleScreen(FriendBattleUiState(), FriendBattleActions())
    }

    /** TC-5896 배치고사 1/3(61355:3061). */
    @Test
    fun placementRound1() = compose.captureRunkkScreen("P1_placement_round1", isDarkTheme = false) {
        PlacementScreen(PlacementUiState(isLoading = false, progress = placing(0)), PlacementActions())
    }

    /** TC-5897 배치고사 2/3(61355:3230), 임시 배지. */
    @Test
    fun placementRound2() = compose.captureRunkkScreen("P1_placement_round2", isDarkTheme = false) {
        PlacementScreen(PlacementUiState(isLoading = false, progress = placing(1, "GOLD")), PlacementActions())
    }

    /** TC-5898 배치고사 3/3(8절 8 연장). */
    @Test
    fun placementRound3() = compose.captureRunkkScreen("P1_placement_round3", isDarkTheme = false) {
        PlacementScreen(PlacementUiState(isLoading = false, progress = placing(2, "SILVER")), PlacementActions())
    }

    /** TC-5899 판 결과 1판 셈(61357:3020), 성과 첫 줄 「승리」. */
    @Test
    fun placementResult() = compose.captureRunkkScreen("P2_placement_result", isDarkTheme = false) {
        PlacementResultScreen(placementResultState(PRS.COUNTED, "WIN", counted = 1), PlacementResultActions())
    }

    /** TC-5900 판 결과 세지 않음(포기 판, 제안 문구). */
    @Test
    fun placementResultNotCounted() =
        compose.captureRunkkScreen("P2_placement_result_not_counted", isDarkTheme = false) {
            PlacementResultScreen(placementResultState(PRS.NOT_COUNTED, "LOSE", counted = 1), PlacementResultActions())
        }

    /** TC-5901 판 결과 정리 중(30초 뒤, 제안 문구). */
    @Test
    fun placementResultPending() = compose.captureRunkkScreen("P2_placement_result_pending", isDarkTheme = false) {
        PlacementResultScreen(
            placementResultState(PRS.PENDING, "WIN", counted = 0, isStillPending = true),
            PlacementResultActions()
        )
    }

    /** 판 셈 정리 중 줄(Surface 바탕, PL2-A4). */
    @Test
    fun placementResultChecking() = compose.captureRunkkScreen("P2_placement_result_checking", isDarkTheme = false) {
        PlacementResultScreen(
            placementResultState(PRS.PENDING, "WIN", counted = 2).copy(detail = null),
            PlacementResultActions()
        )
    }

    /** 30초 뒤 정리 중 안내와 「다시 확인」(PL2-A2). 기록 상세가 아직 없어 안내가 한 화면에 보인다. */
    @Test
    fun placementResultRecheck() = compose.captureRunkkScreen("P2_placement_result_recheck", isDarkTheme = false) {
        PlacementResultScreen(
            placementResultState(PRS.PENDING, "WIN", counted = 2, isStillPending = true).copy(detail = null),
            PlacementResultActions()
        )
    }

    /** 진행 읽기 실패: 진행 카드 자리에 오류와 「다시 시도」(PL2-A2). */
    @Test
    fun placementResultProgressError() =
        compose.captureRunkkScreen("P2_placement_result_progress_error", isDarkTheme = false) {
            PlacementResultScreen(
                placementResultState(PRS.COUNTED, "WIN", counted = 2).copy(
                    detail = null,
                    progress = null,
                    progressError = UiText.Res(R.string.placement_load_failed)
                ),
                PlacementResultActions()
            )
        }

    /** TC-5902 배치고사 완료 브론즈(축하 61361:3142). */
    @Test
    fun placementCompleteBronze() = compose.captureRunkkScreen("P3_placement_complete_bronze", isDarkTheme = false) {
        PlacementCompleteScreen(completeState("BRONZE"), PlacementCompleteActions())
    }

    /** TC-5903 배치고사 완료 실버(축하 61361:3349). */
    @Test
    fun placementCompleteSilver() = compose.captureRunkkScreen("P3_placement_complete_silver", isDarkTheme = false) {
        PlacementCompleteScreen(completeState("SILVER"), PlacementCompleteActions())
    }

    /** TC-5904 배치고사 완료 골드(축하 61361:3556). */
    @Test
    fun placementCompleteGold() = compose.captureRunkkScreen("P3_placement_complete_gold", isDarkTheme = false) {
        PlacementCompleteScreen(completeState("GOLD"), PlacementCompleteActions())
    }

    /** TC-5905 배치고사 완료 플래티넘(축하 61361:3765). */
    @Test
    fun placementCompletePlatinum() =
        compose.captureRunkkScreen("P3_placement_complete_platinum", isDarkTheme = false) {
            PlacementCompleteScreen(completeState("PLATINUM"), PlacementCompleteActions())
        }

    /** TC-5906 배치고사 완료 마스터(축하 61361:3974). */
    @Test
    fun placementCompleteMaster() = compose.captureRunkkScreen("P3_placement_complete_master", isDarkTheme = false) {
        PlacementCompleteScreen(completeState("MASTER"), PlacementCompleteActions())
    }

    /** TC-5907 배치고사 완료 골드 다크(토큰 다크 값). */
    @Test
    fun placementCompleteGoldDark() = compose.captureRunkkScreen("P3_placement_complete_gold_dark") {
        PlacementCompleteScreen(completeState("GOLD"), PlacementCompleteActions())
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

    /** 셀럽런 목록 코치 두 줄(피그마 E-1 60865:7403, 라이트): 코치는 둥근 얼굴, 그 밖의 셀럽은 서 있는 그림. */
    @Test
    fun celebBattleCoaches() = compose.captureRunkkScreen("D1_celeb_battle_coaches", isDarkTheme = false) {
        val coaches = listOf(
            CandidateUi(
                11, "주세빈 코치님",
                Distance(
                    5_000
                ),
                Duration.ofSeconds(1_818), null, null, "출처: 런크크", false, celebrityFigure = "joo"
            ),
            CandidateUi(
                12, "김선교 코치님",
                Distance(
                    5_000
                ),
                Duration.ofSeconds(1_800), null, null, "출처: 런크크", false, celebrityFigure = "kim"
            )
        )
        CelebBattleScreen(
            RecordBattleUiState(isLoading = false, candidates = coaches + celeb),
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
    fun matchFoundGpsSearching() = compose.captureRunkkScreen("D2_match_found_gps_searching") {
        MatchScreen(
            MatchUiState(
                phase = MatchPhase.MATCHED,
                match = matchFound(),
                startsInSeconds = 7,
                myTier = "GOLD",
                gps = GpsReadiness.SEARCHING
            ),
            MatchActions()
        )
    }

    @Test
    fun matchFoundGpsReady() = compose.captureRunkkScreen("D2_match_found_gps_ready") {
        MatchScreen(
            MatchUiState(
                phase = MatchPhase.MATCHED,
                match = matchFound(),
                startsInSeconds = 7,
                myTier = "GOLD",
                gps = GpsReadiness.READY
            ),
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

    /** 배치고사 판(FR-276): 지도 위 상대 자리를 점선 원 「?」로 그린다. */
    @Test
    fun battleRunningPlacement() = compose.captureRunkkScreen("D3_battle_running_placement") {
        BattleRunningScreen(running.copy(placementRound = 1), BattleRunningActions())
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
