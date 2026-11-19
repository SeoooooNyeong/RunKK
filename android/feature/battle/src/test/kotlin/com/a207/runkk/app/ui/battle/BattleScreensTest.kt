package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.CELEBRITY_FIGURE_TAG
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RIVAL_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RUNKK_GUIDANCE_TOAST_TAG
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.TRACK_AHEAD_TAG
import com.a207.runkk.app.core.designsystem.TRACK_BEHIND_TAG
import com.a207.runkk.app.core.designsystem.TRACK_DOT_MINE_TAG
import com.a207.runkk.app.core.designsystem.TRACK_DOT_OTHER_TAG
import com.a207.runkk.app.core.designsystem.TRACK_FINISH_TAG
import com.a207.runkk.app.core.designsystem.TRACK_ME_TAG
import com.a207.runkk.app.core.designsystem.TRACK_OTHER_TAG
import com.a207.runkk.app.core.designsystem.TRACK_RULER_TAG
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.viewModeTag
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 대결 화면: 디자인 문구, 주요 버튼과 접근성 라벨. */
@RunWith(RobolectricTestRunner::class)
class BattleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    /** 1차 배포 모습(아바타 꾸미기 닫힘: 기본 아바타 2표정, D-254)으로 그린다. */
    private fun show(isDark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(darkTheme = isDark, reduceMotion = true) { content() }
            }
        }

    @Test
    fun `랜덤 대결 화면 - 고른 거리로 n km 매칭 시작, 최고 기록이 없으면 안내(FR-285)`() {
        show {
            RandomBattleScreen(
                RandomBattleUiState(),
                RandomBattleActions(onStart = { calls += "start" }, onDistance = { calls += "d:${it.meters}" })
            )
        }
        compose.onNodeWithContentDescription("나 대 아직 모르는 상대", substring = true).assertExists()
        compose.onNodeWithText("아직 기록이 없어요", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("3km").performClick()
        compose.onNodeWithText("5km 매칭 시작").performClick()
        assertEquals(listOf("d:3000", "start"), calls)
    }

    @Test
    fun `D100 친구 대결 초대 버튼은 솟은 탭 원 위에 16을 두고 놓인다(C-9)`() {
        show { FriendBattleScreen(FriendBattleUiState(), FriendBattleActions(onInvite = { calls += "invite" })) }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("카카오톡으로 초대하기").getUnclippedBoundsInRoot()
        assertEquals((16.dp + RUNKK_BOTTOM_BAR_RISE).value, (screen.bottom - button.bottom).value, 1f)
        compose.onNodeWithText("카카오톡으로 초대하기").performClick()
        compose.onNodeWithText("아직 함께 달린 친구가 없어요").assertExists()
        assertEquals(listOf("invite"), calls)
    }

    @Test
    fun `친구 대결 화면 - 친구 라디오와 거리 상자(FR-286)`() {
        val friends = listOf(RecentFriend(21, "바람처럼", LocalDate.of(2026, 10, 12)))
        show {
            FriendBattleScreen(
                FriendBattleUiState(friends = friends, isDistanceMenuOpen = true),
                FriendBattleActions(
                    onFriend = { calls += "f:$it" },
                    onDistanceMenu = { calls += "menu" },
                    onDistance = { calls += "d:${it.meters}" }
                )
            )
        }
        compose.onNodeWithText("10월 12일 함께 달림", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("바람처럼", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("거리 5km").performClick()
        compose.onNodeWithText("10km").performClick()
        assertEquals(listOf("f:21", "menu", "d:10000"), calls)
    }

    @Test
    fun `TC-5876 배치고사 진행을 못 읽으면 시작 버튼을 누를 수 없다`() {
        show { PlacementScreen(PlacementUiState(isLoading = false), PlacementActions(onStart = { calls += "start" })) }
        compose.onNodeWithText("배치고사 1판 시작").performClick()
        compose.onNodeWithText("배치고사 진행을 불러오지 못했어요").assertExists()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `D100 기록 깨러 가기 버튼은 솟은 탭 원 위에 16을 두고 놓인다(D-1)`() {
        val candidates = selfCandidates.map(CandidateUi::of)
        show {
            SelfBattleScreen(
                RecordBattleUiState(isLoading = false, candidates = candidates, selected = candidates[1]),
                onSelect = {},
                onStart = {},
                onRetry = {},
                onMessageShown = {}
            )
        }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("5km 기록 깨러 가기").getUnclippedBoundsInRoot()
        assertEquals((16.dp + RUNKK_BOTTOM_BAR_RISE).value, (screen.bottom - button.bottom).value, 1f)
    }

    @Test
    fun `기록런 분절은 셀프런과 셀럽런이다(D89, R2-13)`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals("셀프런", context.getString(R.string.battle_record_run_self))
        assertEquals("셀럽런", context.getString(R.string.battle_record_run_celeb))
    }

    @Test
    fun `나와의 대결 - 처음엔 시작 버튼이 없고 기록 없는 거리는 누를 수 없다(D-288)`() {
        val candidates = selfCandidates.map(CandidateUi::of)
        show {
            SelfBattleScreen(
                RecordBattleUiState(isLoading = false, candidates = candidates),
                onSelect = { calls += "select:${it.distance.meters}" },
                onStart = {},
                onRetry = {},
                onMessageShown = {}
            )
        }
        compose.onNodeWithText("어제의 나를 이겨 볼까요?").assertExists()
        compose.onNodeWithText("기록 깨러 가기", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("10km, 내 최고 기록 기록 없음", substring = true).assertIsNotEnabled()
        compose.onNodeWithContentDescription("3km, 내 최고 기록 16:00", substring = true).performClick()
        assertEquals(listOf("select:3000"), calls)
    }

    @Test
    fun `나와의 대결 - 카드를 고르면 n km 기록 깨러 가기`() {
        val candidates = selfCandidates.map(CandidateUi::of)
        show {
            SelfBattleScreen(
                RecordBattleUiState(isLoading = false, candidates = candidates, selected = candidates[1]),
                onSelect = {},
                onStart = { calls += "start" },
                onRetry = {},
                onMessageShown = {}
            )
        }
        compose.onNodeWithText("5km 기록 깨러 가기").performClick()
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `나와의 대결 - 거리를 고르기 전에는 상대 자리가 물음표다(FR-성-25)`() {
        val candidates = selfCandidates.map(CandidateUi::of)
        show {
            SelfBattleScreen(RecordBattleUiState(isLoading = false, candidates = candidates), {}, {}, {}, {})
        }
        compose.onNodeWithTag(VERSUS_UNKNOWN_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("?", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("나 대 과거의 나", substring = true).assertExists()
    }

    @Test
    fun `나와의 대결 - 고르면 그 기록에 저장된 모습을 정지 그림 칸에 넘기고 날짜와 기록·페이스를 아이콘 메타로 적는다(FR-성-25)`() {
        val look = RunnerLook(Gender.FEMALE, 12, null, 31, bodyVariant = 1, runAmount = 42, faceKey = "head:7:ab12")
        val picked = CandidateUi(
            celebrityId = null,
            name = "나",
            distance = Distance(5_000),
            finishTime = Duration.ofSeconds(1_625),
            recordedOn = LocalDate.of(2026, 10, 12),
            averagePace = Pace(325),
            sourceLabel = null,
            isEstimated = false,
            look = look
        )
        val subjects = mutableListOf<AvatarStillSubject>()
        val probe: @Composable () -> Unit = {
            val subject = LocalSlotAvatarStill.current
            SideEffect { subject?.let { if (it !in subjects) subjects += it } }
        }
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
                RunkkTheme(reduceMotion = true) {
                    SelfBattleScreen(
                        RecordBattleUiState(isLoading = false, candidates = listOf(picked), selected = picked),
                        {},
                        {},
                        {},
                        {}
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(
            setOf(MY_STILL, AvatarStillSubject(isMine = false, isFemale = true, slotLook = look.toSlot())),
            subjects.toSet()
        )
        compose.onAllNodesWithTag(VERSUS_UNKNOWN_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("나 대 과거의 나", substring = true)
            .assert(hasContentDescription("10월 12일", substring = true))
        // 기록 시간과 페이스는 가운뎃점 없이 항목마다 아이콘을 둔 메타 줄이다.
        compose.onAllNodesWithText("27:05", useUnmergedTree = true).assertCountEquals(2) // 메타 줄과 거리 카드
        compose.onNodeWithText("5:25 /km", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `성사와 결과의 상대 전신은 받은 모습으로, 없으면 표시 성별의 기본 모습으로 그린다(FR-성-29)`() {
        val look = RunnerLook(Gender.FEMALE, 12, null, 31, bodyVariant = 1, runAmount = 42, faceKey = "head:7:ab12")
        assertEquals(
            AvatarStillSubject(isMine = false, isFemale = true, slotLook = look.toSlot()),
            rivalStill(AvatarCharacter.MALE, look),
            "받은 모습의 성별이 이긴다"
        )
        assertEquals(AvatarStillSubject(isMine = false, isFemale = false), rivalStill(AvatarCharacter.MALE, null))
        assertEquals(AvatarStillSubject(isMine = false, isFemale = true), rivalStill(AvatarCharacter.FEMALE, null))
    }

    @Test
    fun `그날의 나는 받은 모습이 없으면 지금 내 모습이다(D-성-272)`() {
        assertEquals(MY_STILL, pastSelfStill(null))
        val male = RunnerLook(Gender.MALE, null, null, null, 0, 0, null)
        assertEquals(
            AvatarStillSubject(isMine = false, isFemale = false, slotLook = male.toSlot()),
            pastSelfStill(male)
        )
    }

    @Test
    fun `셀럽런 - 출처는 서버 문구 그대로, 카드를 고르면 대결하기로 시작한다(D-288)`() {
        val candidates = celebCandidates.map(CandidateUi::of)
        var state by mutableStateOf(RecordBattleUiState(isLoading = false, candidates = candidates))
        show {
            CelebBattleScreen(
                state,
                onFilter = {},
                onSelect = {
                    calls += "select:${it.celebrityId}"
                    state = state.copy(selected = it)
                },
                onStart = { calls += "start" },
                onRetry = {},
                onMessageShown = {}
            )
        }
        compose.onNodeWithText("공개 기록 기준 · 2025 서울마라톤").assertExists()
        compose.onNodeWithText("대결하기").assertDoesNotExist()
        compose.onNodeWithContentDescription("번개발", substring = true).performClick()
        compose.onNodeWithText("대결하기").performClick()
        assertEquals(listOf("select:7", "start"), calls)
    }

    @Test
    fun `매칭 대기 - 경과 시간을 읽어 주고 취소 버튼이 있다(C-2)`() {
        show {
            MatchScreen(
                MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7),
                MatchActions(onCancel = {
                    calls +=
                        "cancel"
                })
            )
        }
        compose.onNodeWithText("배틀런 · 5km").assertDoesNotExist()
        compose.onNodeWithContentDescription("상대를 찾고 있어요, 0:07 지남").assertExists()
        // 이마에 손 얹고 찾는 자세(D84, 피그마 C-2)
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "SEARCH", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("취소").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `TC-9663 성사 - 나와 상대 모두 한글 티어 칩이 있다(C-3, R2-10)`() {
        show {
            MatchScreen(
                MatchUiState(phase = MatchPhase.MATCHED, match = matchFound(), startsInSeconds = 7, myTier = "GOLD"),
                MatchActions()
            )
        }
        compose.onNodeWithText("상대를 찾았어요").assertExists()
        compose.onNodeWithText("골드").assertExists()
        compose.onNodeWithText("실버").assertExists()
        compose.onNodeWithText("GOLD").assertDoesNotExist()
    }

    @Test
    fun `TC-9663 성사 - 내 티어를 모르면 내 칩은 비운다`() {
        show {
            MatchScreen(MatchUiState(phase = MatchPhase.MATCHED, match = matchFound()), MatchActions())
        }
        compose.onNodeWithText("실버").assertExists()
        compose.onNodeWithText("골드").assertDoesNotExist()
    }

    @Test
    fun `TC-9665 초대 대기 - 머리, 안내, 경과, 다시 보내기·취소(C-10)`() {
        show {
            MatchScreen(
                MatchUiState(phase = MatchPhase.INVITING, opponentKind = OpponentKind.FRIEND, waitingSeconds = 300),
                MatchActions(onResend = { calls += "resend" }, onCancel = { calls += "cancel" })
            )
        }
        compose.onNodeWithText("배틀런 · 친구 대결 · 5km").assertDoesNotExist()
        compose.onNodeWithText("친구가 들어오길 기다리고 있어요").assertExists()
        compose.onNodeWithText("5:00").assertExists()
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "SEARCH", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("카카오톡으로 다시 보내기").performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(listOf("resend", "cancel"), calls)
    }

    @Test
    fun `TC-9679 참가 확인 - 양쪽 아바타·티어·최고 기록, 언랭 표기, 거절·참가하기(C-12)`() {
        show {
            MatchScreen(
                MatchUiState(phase = MatchPhase.CONFIRMING, opponentKind = OpponentKind.FRIEND, invite = inviteView()),
                MatchActions(onDecline = { calls += "decline" }, onAccept = { calls += "accept" })
            )
        }
        compose.onNodeWithText("친구가 대결을 신청했어요").assertExists()
        compose.onNodeWithText("바람처럼").assertExists()
        // 서버 티어 코드는 한글 이름으로 보인다(R2-11)
        compose.onNodeWithText("골드").assertExists()
        compose.onNodeWithText("GOLD").assertDoesNotExist()
        compose.onNodeWithText("배치 중").assertExists()
        compose.onNodeWithText("5km 최고 30:12").assertExists()
        compose.onNodeWithText("5km 최고 29:48").assertExists()
        compose.onNodeWithText("거절").performClick()
        compose.onNodeWithText("참가하기").performClick()
        assertEquals(listOf("decline", "accept"), calls)
    }

    @Test
    fun `TC-9679 최고 기록이 없으면 그 칸을 비운다`() {
        show {
            MatchScreen(
                MatchUiState(
                    phase = MatchPhase.CONFIRMING,
                    opponentKind = OpponentKind.FRIEND,
                    invite = inviteView().copy(myBestSeconds = null)
                ),
                MatchActions()
            )
        }
        compose.onNodeWithText("5km 최고 30:12").assertDoesNotExist()
        compose.onNodeWithText("5km 최고 29:48").assertExists()
    }

    @Test
    fun `안내를 확인하면 이전 화면으로`() {
        show {
            MatchScreen(
                MatchUiState(phase = MatchPhase.CONFIRMING, notice = UiText.Res(R.string.battle_join_own)),
                MatchActions(onNoticeConfirmed = { calls += "ok" })
            )
        }
        compose.onNodeWithText("내가 보낸 초대예요").assertExists()
        compose.onNodeWithText("확인").performClick()
        assertEquals(listOf("ok"), calls)
    }

    @Test
    fun `매칭 대기 - 화면을 나가면 취소된다는 안내를 늘 보이고 확인 창은 나가기와 계속 기다리기다(FR-성-32)`() {
        var state by mutableStateOf(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7))
        show {
            MatchScreen(
                state,
                MatchActions(onLeaveConfirm = { calls += "leave" }, onLeaveDismiss = { calls += "stay" })
            )
        }
        compose.onNodeWithText("화면을 나가면 매칭이 취소돼요").assertExists()
        compose.onNodeWithText("매칭을 취소할까요?").assertDoesNotExist()

        state = state.copy(isLeaveConfirmVisible = true)
        compose.waitForIdle()
        compose.onNodeWithText("매칭을 취소할까요?").assertExists()
        compose.onNodeWithText("지금 나가면 매칭이 취소돼요. 상대가 정해진 후에 나가면 패배로 기록돼요.").assertExists()
        compose.onNodeWithText("계속 기다리기").performClick()
        compose.onNodeWithText("나가기").performClick()
        assertEquals(listOf("stay", "leave"), calls)
    }

    @Test
    fun `친구 초대 대기 - 안내와 확인 창 본문이 초대 문구다(FR-성-32)`() {
        show {
            MatchScreen(
                MatchUiState(
                    phase = MatchPhase.INVITING,
                    opponentKind = OpponentKind.FRIEND,
                    isLeaveConfirmVisible = true
                ),
                MatchActions()
            )
        }
        compose.onNodeWithText("화면을 나가면 초대가 취소돼요").assertExists()
        compose.onNodeWithText("매칭을 취소할까요?").assertExists()
        compose.onNodeWithText("지금 나가면 초대가 취소돼요.").assertExists()
        compose.onNodeWithText("화면을 나가면 매칭이 취소돼요").assertDoesNotExist()
    }

    private val route = RouteLine(
        listOf(
            GeoPoint(37.5000, 127.0000),
            GeoPoint(37.5006, 127.0004),
            GeoPoint(37.5012, 127.0012),
            GeoPoint(37.5016, 127.0024)
        )
    )

    @Test
    fun `TC-9646 대결 진행 - 거리 차와 앞서는 중, 상대 숫자는 없고 트랙 밖 상대는 가장자리 칩이다(C-4, FR-성-27)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(
                    battleId = 9,
                    opponentName = "바람처럼",
                    phase = BattlePhase.RUNNING,
                    gapMeters = 42,
                    opponentDistance = Distance(2_800),
                    myDistance = Distance(2_842),
                    route = route
                ),
                BattleRunningActions()
            )
        }
        compose.onNodeWithText("배틀런 · 5km").assertDoesNotExist()
        compose.onNodeWithContentDescription("상대와 거리 차 42미터 앞서는 중").assertExists()
        compose.onNodeWithText("2.80", substring = true).assertDoesNotExist()
        // 42 m 뒤의 상대는 화면 폭(30 m) 밖이라 아바타 없이 왼쪽 가장자리 칩(이름과 차이)이다.
        compose.onNodeWithTag(BATTLE_TRACK_TAG).assertExists()
        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
        compose.onNodeWithTag(TRACK_BEHIND_TAG).assertExists()
        compose.onNodeWithText("‹ 바람처럼 42 m").assertExists()
        compose.onNodeWithText("2.84 / 5 km").assertExists()
        compose.onNodeWithText("남은 거리 2.16 km").assertExists()
        // 지도를 띄울 수 없는 환경(시험)에서는 전환을 숨기고 트랙만 보인다.
        compose.onAllNodesWithTag(viewModeTag(RunViewMode.MAP)).assertCountEquals(0)
    }

    @Test
    fun `대결 진행 트랙 - 창 안의 상대는 위 레인에 이름표와 함께 그리고 거리 자에 차이를 적는다(FR-성-27)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(
                    battleId = 9,
                    opponentName = "바람처럼",
                    phase = BattlePhase.RUNNING,
                    gapMeters = -12,
                    opponentDistance = Distance(2_354),
                    myDistance = Distance(2_342),
                    currentPace = Pace(330)
                ),
                BattleRunningActions()
            )
        }
        compose.onNodeWithTag(TRACK_OTHER_TAG).assertExists()
        compose.onNodeWithTag(TRACK_ME_TAG).assertExists()
        compose.onNodeWithTag(TRACK_RULER_TAG).assertTextEquals("12 m")
        compose.onNodeWithText("바람처럼").assertExists()
        compose.onAllNodesWithText("5:30").assertCountEquals(2) // 지표 줄의 현재 페이스와 내 이름표
        compose.onAllNodesWithTag(TRACK_AHEAD_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_BEHIND_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_DOT_MINE_TAG).assertCountEquals(1)
        compose.onAllNodesWithTag(TRACK_DOT_OTHER_TAG).assertCountEquals(1)
    }

    @Test
    fun `대결 진행 트랙 - 결승선은 남은 거리가 화면 안에 들어올 때만 그린다(D-성-306)`() {
        var state by mutableStateOf(
            BattleRunningUiState(battleId = 9, opponentName = "바람처럼", phase = BattlePhase.RUNNING, gapMeters = 3)
                .copy(myDistance = Distance(4_975))
        )
        show { BattleRunningScreen(state, BattleRunningActions()) }
        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)

        state = state.copy(myDistance = Distance(4_990))
        compose.waitForIdle()
        compose.onNodeWithTag(TRACK_FINISH_TAG).assertExists()
    }

    @Test
    fun `대결 진행 트랙 - 배치고사 상대는 물음표를 유지하고 격차를 모르면 상대를 그리지 않는다(FR-276)`() {
        var state by mutableStateOf(
            BattleRunningUiState(
                battleId = 9,
                opponentName = "느긋한고래",
                phase = BattlePhase.RUNNING,
                gapMeters = 5,
                placementRound = 1
            )
        )
        show { BattleRunningScreen(state, BattleRunningActions()) }
        compose.onNodeWithTag(TRACK_OTHER_TAG).assertExists()
        compose.onNodeWithText("?", useUnmergedTree = true).assertExists()

        state = state.copy(gapMeters = null)
        compose.waitForIdle()
        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
    }

    @Test
    fun `대결 진행 - 짧게 누르면 안내, 길게 누르면 그만두기 확인을 부른다(D-49)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(battleId = 9, opponentName = "바람처럼", phase = BattlePhase.RUNNING, gapMeters = -15),
                BattleRunningActions(onForfeitTap = { calls += "tap" }, onForfeitLongPress = { calls += "long" })
            )
        }
        compose.onNodeWithContentDescription("대결 그만두기").performClick()
        compose.onNodeWithContentDescription("대결 그만두기").performTouchInput { longClick() }
        assertEquals(listOf("tap", "long"), calls)
        compose.onNodeWithContentDescription("상대와 거리 차 15미터 뒤처지는 중").assertExists()
    }

    @Test
    fun `대결 진행 - 상대 연결 상태를 알린다(FR-088)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(
                    battleId = 9,
                    opponentName = "바람처럼",
                    phase = BattlePhase.RUNNING,
                    gapMeters = 10,
                    connectionLabel = UiText.Res(R.string.battle_running_checking)
                ),
                BattleRunningActions()
            )
        }
        compose.onNodeWithText("상대 연결 확인 중")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("잠금").assertDoesNotExist()
    }

    @Test
    fun `대결 진행 - 추월·역전·종료 카운트다운·힌트·오류 글자가 떠도 트랙 칸 크기와 자리는 그대로다(S15P31A207-334)`() {
        assertTrackStaysPut(OpponentKind.RANDOM)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `작은 화면 셀프런 - 안내가 떠도 트랙 칸과 내 아바타 자리가 그대로다(S15P31A207-334)`() {
        assertTrackStaysPut(OpponentKind.SELF)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun `큰 화면 셀럽런 - 안내가 떠도 트랙 칸과 내 아바타 자리가 그대로다(S15P31A207-334)`() {
        assertTrackStaysPut(OpponentKind.CELEBRITY)
    }

    @Test
    fun `대결 진행 - 추월·역전 안내는 큰 거리 숫자 위에 흰 띠로 겹쳐 뜨고 사라진다(S15P31A207-334 2차)`() {
        assertGuidanceFloats(OpponentKind.RANDOM, isDark = false)
    }

    @Test
    fun `다크 셀프런 - 앞지름·역전 안내도 흰 띠로 큰 거리 숫자 위에 겹친다(S15P31A207-334 2차)`() {
        assertGuidanceFloats(OpponentKind.SELF, isDark = true)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `작은 화면 셀럽런 - 추월·역전 안내가 겹쳐 떠도 거리 숫자와 트랙 칸은 그대로다(S15P31A207-334 2차)`() {
        assertGuidanceFloats(OpponentKind.CELEBRITY, isDark = false)
    }

    /**
     * 안내 없음 → 추월 → 역전 → 종료 카운트다운 → 없음. 안내 띠는 큰 거리 숫자와 겹치는 자리(세로 줄의 형제라면 겹칠 수 없다)에 흰 배경으로
     * 뜨고, 트랙 칸보다 위에 있으며, 바로 읽힌다. 사라지면 노드가 없다.
     */
    private fun assertGuidanceFloats(kind: OpponentKind, isDark: Boolean) {
        val base = runningBase(kind)
        var state by mutableStateOf(base)
        show(isDark) { BattleRunningScreen(state, BattleRunningActions()) }
        compose.onNodeWithTag(GUIDANCE_TOAST, useUnmergedTree = true).assertDoesNotExist()
        val overtook = if (kind == OpponentKind.SELF) "과거의 나를 앞질렀어요" else "추월했어요!"
        listOf(
            base.copy(guidance = BattleGuidance.Kind.OVERTOOK) to overtook,
            base.copy(guidance = BattleGuidance.Kind.OVERTAKEN) to "역전당했어요"
        ).forEach { (variant, text) ->
            state = variant
            compose.waitForIdle()
            val toast = compose.onNodeWithTag(GUIDANCE_TOAST, useUnmergedTree = true)
            val bounds = toast.getUnclippedBoundsInRoot()
            val distance = compose.onNodeWithTag(BATTLE_DISTANCE, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val view = compose.onNodeWithTag(BATTLE_VIEW_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(
                bounds.top < distance.bottom && bounds.bottom > distance.top,
                "$text 띠가 거리 숫자와 겹친다: $bounds / $distance"
            )
            assertTrue(bounds.top < view.top, "$text 띠는 트랙 칸보다 위에 뜬다: $bounds / $view")
            compose.onNodeWithText(text)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            val image = toast.captureToImage().asAndroidBitmap()
            val edge = with(compose.density) { 8.dp.roundToPx() }
            assertEquals(
                android.graphics.Color.WHITE,
                image.getPixel(image.width - edge, image.height / 2),
                "$text 띠 바탕은 테마와 상관없이 흰색이다(다크 $isDark)"
            )
        }
        state =
            base.copy(phase = BattlePhase.CLOSING, arrivedName = "바람처럼", closingSeconds = 27, closingLimitSeconds = 30)
        compose.waitForIdle()
        compose.onNodeWithTag(GUIDANCE_TOAST, useUnmergedTree = true).assertExists()
        state = base
        compose.waitForIdle()
        compose.onNodeWithTag(GUIDANCE_TOAST, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun runningBase(kind: OpponentKind) = BattleRunningUiState(
        battleId = 9,
        opponentName = "바람처럼",
        kind = kind,
        phase = BattlePhase.RUNNING,
        gapMeters = -12,
        opponentDistance = Distance(2_354),
        myDistance = Distance(2_342),
        currentPace = Pace(330)
    )

    /** 안내 없는 상태에서 트랙(또는 지도) 칸·내 아바타·큰 거리 숫자의 위·높이를 재고, 안내마다 같은지 본다. 안내 글자는 바로 읽힌다. */
    private fun assertTrackStaysPut(kind: OpponentKind) {
        val base = runningBase(kind)
        var state by mutableStateOf(base)
        show { BattleRunningScreen(state, BattleRunningActions()) }
        fun measure() = listOf(BATTLE_VIEW_TAG, TRACK_ME_TAG, BATTLE_DISTANCE).map { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
                .let { it.top to it.bottom - it.top }
        }
        val expected = measure()
        val variants = listOf(
            base.copy(guidance = BattleGuidance.Kind.OVERTOOK),
            base.copy(guidance = BattleGuidance.Kind.OVERTAKEN),
            base.copy(phase = BattlePhase.CLOSING, arrivedName = "바람처럼", closingSeconds = 27, closingLimitSeconds = 30),
            base.copy(hint = UiText.Plain("길게 눌러 종료해요")),
            base.copy(error = UiText.Plain("상대 기록을 받지 못했어요")),
            base.copy(guidance = BattleGuidance.Kind.OVERTAKEN, hint = UiText.Plain("길게 눌러 종료해요"))
        )
        variants.forEach { variant ->
            state = variant
            compose.waitForIdle()
            assertEquals(expected, measure(), "안내 상태 $variant")
        }
        state = base.copy(guidance = BattleGuidance.Kind.OVERTAKEN)
        compose.waitForIdle()
        compose.onNodeWithText("역전당했어요")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        state = base
        compose.waitForIdle()
        assertEquals(expected, measure(), "안내가 사라진 뒤")
    }

    @Test
    fun `TC-9739 대결 탭은 대결·승·패 수, 필터 칩, 날짜·종류·거리·상대·승패 배지를 보이고 누르면 대결 결과로 간다`() {
        val filters = mutableListOf<Int>()
        val opened = mutableListOf<Long>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleHistoryTabContent(
                    BattleHistoryUiState(isLoading = false, history = history()),
                    onFilter = { filters += it },
                    onEntry = { opened += it },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("대결").assertExists()
        listOf("전체", "배틀런", "셀프런", "셀럽런").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("배틀런", substring = true).assertCountEquals(2)
        compose.onNodeWithText("바람처럼").assertExists()
        compose.onNodeWithText("셀프런").performClick()
        assertEquals(listOf(2), filters)
        compose.onNodeWithText("바람처럼").performClick()
        assertEquals(listOf(9L), opened)
        compose.onNodeWithText("이번 주 전적", substring = true).assertDoesNotExist()
    }

    @Test
    fun `TC-9637 셀프런 그만두기 확인 문구(패배로 기록, D119)와 계속 달리기(D-5)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(kind = OpponentKind.SELF, phase = BattlePhase.RUNNING, isConfirmingForfeit = true),
                BattleRunningActions(onForfeitDismiss = { calls += "keep" })
            )
        }
        compose.onNodeWithText("셀프런을 그만둘까요?").assertExists()
        compose.onNodeWithText("그만두면 패배로 기록되고, 지금까지 달린 기록은 남아요.").assertExists()
        compose.onNodeWithText("계속 달리기").performClick()
        assertEquals(listOf("keep"), calls)
    }

    @Test
    fun `셀프런 카운트다운 - 과거의 나와 대결해요, 날짜·기록, 다시 고르기(D-2)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(
                    kind = OpponentKind.SELF,
                    phase = BattlePhase.COUNTDOWN,
                    countdown = 2,
                    opponentRecord = Duration.ofSeconds(1_812),
                    opponentRecordedOn = LocalDate.of(2026, 10, 2)
                ),
                BattleRunningActions(onReselect = { calls += "reselect" })
            )
        }
        compose.onNodeWithText("과거의 나와 대결해요").assertExists()
        compose.onNodeWithText("10월 2일의 나").assertExists()
        compose.onNodeWithText("5km 기록 30:12").assertExists()
        compose.onNodeWithText("초 뒤 자동으로 출발해요").assertExists()
        compose.onNodeWithText("다시 고르기").performClick()
        assertEquals(listOf("reselect"), calls)
    }

    @Test
    fun `셀럽런 카운트다운 - 이름과 달려요, 첫 도전, 출처 없음(E-2)`() {
        show {
            BattleRunningScreen(
                BattleRunningUiState(
                    opponentName = "김선교 대공님",
                    kind = OpponentKind.CELEBRITY,
                    distance = Distance(10_000),
                    phase = BattlePhase.COUNTDOWN,
                    countdown = 2,
                    opponentRecord = Duration.ofSeconds(3_520)
                ),
                BattleRunningActions()
            )
        }
        compose.onNodeWithText("김선교 대공님과 달려요").assertExists()
        compose.onNodeWithText("첫 도전").assertExists()
        compose.onNodeWithText("10km 기록 58:40").assertExists()
        compose.onNodeWithText("다시 고르기").assertDoesNotExist()
        compose.onNodeWithText("출처", substring = true).assertDoesNotExist()
    }

    @Test
    fun `TC-9651 TC-9653 대결 결과 - 제목·큰 제목·문구·기쁜 아바타, 밀어서 상세, 기록 카드·홈으로`() {
        show {
            BattleResultScreen(
                BattleResultUiState(isLoading = false, result = battleResult()),
                onCard = { calls += "card" },
                onHome = { calls += "home" },
                onRetry = {}
            )
        }
        compose.onNodeWithText("대결 결과").assertExists()
        compose.onNodeWithContentDescription("승리!. 바람처럼님보다 42초 먼저 도착했어요").assertExists()
        // 내 아바타(승리 JOY)와 배틀런 상대(공통 상대 그림, D73).
        compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + "JOY", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(RIVAL_AVATAR_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("밀어서 상세 결과 보기 ›").assertExists()
        compose.onNodeWithText("이번 주", substring = true).assertDoesNotExist()
        compose.onNodeWithText("함께 달린 기록", substring = true).assertDoesNotExist()
        compose.onNodeWithText("기록 카드").performClick()
        compose.onNodeWithText("홈으로").performClick()
        assertEquals(listOf("card", "home"), calls)
    }

    @Test
    fun `TC-9652 셀프런 결과 - 셀프런 결과·신기록!·비교표 2줄`() {
        show {
            BattleResultScreen(
                BattleResultUiState(
                    isLoading = false,
                    result = battleResult().copy(
                        type = BattleHistory.Type.SELF,
                        opponentName = "10월 2일의 나",
                        rows = listOf(
                            com.a207.runkk.app.domain.battle.BattleResult.Row("기록", "29:58", "30:12"),
                            com.a207.runkk.app.domain.battle.BattleResult.Row("평균 페이스", "5:59", "6:02")
                        )
                    )
                ),
                {},
                {},
                {}
            )
        }
        compose.onNodeWithText("셀프런 결과").assertExists()
        compose.onNodeWithText("신기록!").assertExists()
        compose.onNodeWithContentDescription("기록, 나 29:58, 상대 30:12").assertExists()
    }

    @Test
    fun `TC-9653 셀럽런 패배 - 슬픈 아바타와 공통 SD`() {
        show {
            BattleResultScreen(
                BattleResultUiState(
                    isLoading = false,
                    result = battleResult(verdict = "LOSE").copy(type = BattleHistory.Type.CELEBRITY)
                ),
                {},
                {},
                {}
            )
        }
        compose.onNodeWithText("셀럽런 결과").assertExists()
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "SAD", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(CELEBRITY_FIGURE_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `대결 결과 - 본인 기록 ID가 아직 없으면 기록 목록 단추`() {
        show {
            BattleResultScreen(
                BattleResultUiState(isLoading = false, result = battleResult(recordId = null)),
                onCard = { calls += "records" },
                onHome = {},
                onRetry = {}
            )
        }
        compose.onNodeWithText("기록 목록").performClick()
        assertEquals(listOf("records"), calls)
    }
}

/** 안내 띠와 큰 거리 숫자의 시험 표지. */
private const val GUIDANCE_TOAST = RUNKK_GUIDANCE_TOAST_TAG
private const val BATTLE_DISTANCE = BATTLE_DISTANCE_TAG
