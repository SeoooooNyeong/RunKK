package com.a207.runkk.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.runkkAvatarCharacterTag
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 홈 화면(A-4, D-276). 시험 설계 10-06 TC-9721, TC-9700(아바타 꾸미기 닫힘), TC-9701(기본 아바타), TC-9685(위치 동의 시트). */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val routes = mutableListOf<String>()
    private var freeRuns = 0

    private val loaded = HomeUiState(
        isLoading = false,
        header = HomeHeader("달리는곰", "골드", 3, 1_250),
        isHeaderLoading = false,
        isAvatarReady = true,
        weeklyDistance = Distance(12_000),
        weeklyGoal = Distance(20_000),
        streakWeeks = 4,
        faceKey = "default:BEAR",
        tierDisplay = TierDisplay.Confirmed("GOLD")
    )
    private var battleCards = 0
    private val latestRuns = mutableListOf<String>()
    private var summaryToggles = 0

    private fun show(state: HomeUiState, scope: ReleaseScope = ReleaseScope.FIRST_RELEASE) {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides scope) {
                RunkkTheme(reduceMotion = true) {
                    HomeScreen(
                        state,
                        HomeActions(
                            onNavigate = { routes += it },
                            onFreeRun = { freeRuns++ },
                            onBattleCard = { battleCards++ },
                            onSummaryToggle = { summaryToggles++ },
                            onLatestRun = { latestRuns += it }
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `TC-9721 상단·주간 막대·연속·두 카드를 디자인 문구로 보인다`() {
        show(loaded)
        compose.onNodeWithText("달리는곰").assertExists()
        // 홈 헤더 티어는 아주 작은 배지 + 「Lv.n」이다(FR-288).
        compose.onNodeWithContentDescription("골드 티어", substring = true).assertExists()
        compose.onNodeWithText("Lv.3", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("1,250 P", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("이번 주 12킬로미터, 목표 20킬로미터, 4주 연속").assertExists()
        compose.onNodeWithContentDescription("자유런, 음성 켬, 진동 켬").assertExists()
        compose.onNodeWithContentDescription("배틀런, 랜덤 또는 친구와 대결").assertExists()
    }

    @Test
    fun `W48 미전송 기록 안내가 필요하면 상단에 재전송 안내 띠를 보인다`() {
        show(loaded.copy(isResendReminderVisible = true))
        compose.onNodeWithText("올리지 못한 기록이 있어요. 인터넷에 연결해 주세요").assertExists()
    }

    @Test
    fun `W48 안내가 필요 없으면 띠를 보이지 않는다`() {
        show(loaded)
        compose.onNodeWithText("올리지 못한 기록이 있어요. 인터넷에 연결해 주세요").assertDoesNotExist()
    }

    @Test
    fun `TC-9721 오늘·누적 요약, 랜드마크, 잔디, 횟수 달성률은 보이지 않는다`() {
        show(loaded)
        listOf("오늘 달린 거리", "이번 주 목표", "첫 랜드마크를 향해", "우리 집", "자유런 시작", "랜덤 매칭 시작").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `자유런 카드는 시작을 부르고 배틀런 카드는 진입 시트, 해 버튼은 러닝 설정, 종은 알림함으로 간다`() {
        show(loaded)
        compose.onNodeWithContentDescription("자유런, 음성 켬, 진동 켬").performClick()
        compose.onNodeWithContentDescription("배틀런, 랜덤 또는 친구와 대결").performClick()
        compose.onNodeWithContentDescription("러닝 설정").performClick()
        compose.onNodeWithContentDescription("알림함").performClick()
        assertEquals(1, freeRuns)
        assertEquals(1, battleCards, "배틀런 카드는 홈 위 진입 시트를 연다(D-499)")
        assertEquals(listOf(Routes.FREE_RUN_SETTINGS, Routes.NOTIFICATIONS), routes)
    }

    @Test
    fun `음성·진동이 꺼져 있으면 off로 보인다`() {
        show(loaded.copy(isRunVoiceOn = false, isRunVibrationOn = true))
        compose.onNodeWithText("음성 off · 진동 on", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-9700 TC-9701 1차 배포는 기본 아바타를 그리고 아바타·잔액을 눌러도 이동하지 않는다`() {
        show(loaded)
        // 상단은 얼굴, 가운데는 서 있는 모습(피그마 A-4, D82)
        compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + AvatarExpression.FACE.name, useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + AvatarExpression.STAND.name, useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("내 아바타").assertHasNoClickAction()
        compose.onNodeWithContentDescription("리워드 잔액 1,250 포인트").assertHasNoClickAction()
    }

    @Test
    fun `TC-9700 아바타 꾸미기가 열리면 얼굴 그림을 쓰고 아바타는 꾸미기, 잔액은 상점으로 간다`() {
        show(loaded, ReleaseScope.ALL_OPEN)
        compose.onNodeWithContentDescription("내 아바타").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("리워드 잔액 1,250 포인트").performClick()
        assertEquals(listOf(Routes.AVATAR, Routes.SHOP), routes)
    }

    @Test
    fun `TC-9743 목표가 없으면 그 자리를 비우고 잔액을 모르면 0 P로 보인다`() {
        show(loaded.copy(weeklyGoal = null, header = HomeHeader("달리는곰", null, null, null), tierDisplay = null))
        compose.onNodeWithText("0 P", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("- P", useUnmergedTree = true).assertDoesNotExist()
        // 배치 진행을 못 읽으면 배지 없이 Lv만 보인다(TC-5837).
        compose.onNodeWithText("Lv.-", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("배치 중", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("이번 주 12킬로미터, 목표 없음, 4주 연속").assertExists()
    }

    @Test
    fun `TC-9685 위치 동의 시트는 디자인 문구로 보이고 버튼이 행동을 부른다`() {
        var agreed = 0
        var closed = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                HomeScreen(
                    loaded.copy(isConsentSheetVisible = true),
                    HomeActions(onConsentAgree = { agreed++ }, onConsentClose = { closed++ })
                )
            }
        }
        compose.onNodeWithText("위치 정보 동의가 필요해요").assertExists()
        compose.onNodeWithText("러닝을 기록하려면 위치 정보 수집에 동의해 주세요.").assertExists()
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(1, agreed)
        assertEquals(1, closed)
    }

    @Test
    @Config(qualifiers = RUNKK_PHONE)
    fun `R2-06 배경 장면 카드는 하단 탭 바 뒤로 화면 아래 끝 가까이까지 이어진다`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    // 앱처럼 홈 자리는 하단 탭 높이만큼 위에서 끝난다(RunkkNavHost EntryBottomSpace).
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(loaded, HomeActions(), Modifier.padding(bottom = RUNKK_BOTTOM_BAR_HEIGHT))
                    }
                }
            }
        }
        val room = compose.onNodeWithTag(HOME_ROOM_TAG).getUnclippedBoundsInRoot()
        // 화면 844dp, 카드 아래 끝은 화면 끝에서 8dp 위(탭 바 자리 80dp를 넘어선다).
        assertEquals(836f, room.bottom.value, 1f)
    }

    @Test
    fun `TC-5841 기록이 없으면 최근 러닝 자리에 안내를 보인다`() {
        val today = java.time.LocalDate.of(2026, 11, 4)
        show(
            loaded.copy(
                isSummaryExpanded = true,
                summary = HomeSummary(
                    today = null,
                    month = MonthSummary(Distance.ZERO, 0),
                    latestRun = null,
                    grass = GrassWeeks.of(emptyMap(), today)
                )
            )
        )
        compose.onNodeWithText("아직 러닝 기록이 없어요").assertExists()
        compose.onNodeWithContentDescription("오늘, 오늘은 아직 달리지 않았어요").assertExists()
        compose.onNodeWithText("접기", useUnmergedTree = true).performClick()
        assertEquals(1, summaryToggles)
    }

    @Test
    fun `TC-4842 펼친 패널의 오늘·이번 달·러닝 잔디를 누르면 러닝 기록 화면으로 간다`() {
        val today = java.time.LocalDate.of(2026, 11, 4)
        show(
            loaded.copy(
                isSummaryExpanded = true,
                summary = HomeSummary(
                    today = null,
                    month = MonthSummary(Distance(41_200), 9),
                    latestRun = null,
                    grass = GrassWeeks.of(emptyMap(), today)
                )
            )
        )
        // 화면 낭독기가 두 번 눌러 열 수 있게 칸마다 누르기 동작이 남아 있어야 한다(clearAndSetSemantics가 지우지 않음)
        compose.onNodeWithContentDescription("오늘, 오늘은 아직 달리지 않았어요").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("이번 달", substring = true).assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("러닝 잔디", substring = true).assertHasClickAction().performClick()
        assertEquals(listOf(Routes.RECORDS, Routes.RECORDS, Routes.RECORDS), routes)
    }

    @Test
    fun `TC-5841 접힌 홈은 「내 기록 요약」 단추만 있고 누르면 펼친다`() {
        show(loaded)
        compose.onNodeWithText("최근 러닝").assertDoesNotExist()
        compose.onNodeWithText("내 기록 요약", useUnmergedTree = true).performClick()
        assertEquals(1, summaryToggles)
    }

    @Test
    fun `TC-5840 펼친 패널의 최근 러닝을 누르면 그 기록을 연다`() {
        val today = java.time.LocalDate.of(2026, 11, 4)
        val run = com.a207.runkk.app.domain.record.RunSummary(
            recordId = "run-9",
            startedAt = java.time.Instant.parse("2026-11-03T22:10:00Z"),
            distance = Distance(3_200),
            elapsed = java.time.Duration.ofSeconds(1_120),
            averagePace = com.a207.runkk.app.core.model.Pace(350),
            isValid = true,
            mode = com.a207.runkk.app.core.model.RunMode.FREE,
            cardKind = com.a207.runkk.app.core.model.CardKind.FREE_RUN
        )
        show(
            loaded.copy(
                isSummaryExpanded = true,
                summary = HomeSummary(null, MonthSummary(Distance(41_200), 9), run, GrassWeeks.of(emptyMap(), today))
            )
        )
        compose.onNodeWithContentDescription("평균 페이스", substring = true).performClick()
        assertEquals(listOf("run-9"), latestRuns)
    }

    @Test
    fun `TC-5842 진입 시트는 배치 중과 확정 티어 문구가 다르다`() {
        val placing = PlacementProgress(PlacementStatus.IN_PROGRESS, 1, 2, "GOLD", "UNRANKED", emptyList())
        val ranked = PlacementProgress(PlacementStatus.COMPLETED, 3, null, null, "GOLD", emptyList())
        var randoms = 0
        var friends = 0
        var state by androidx.compose.runtime.mutableStateOf(BattleEntrySheetState(placing))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleEntrySheetContent(state, onRandom = { randoms++ }, onFriend = { friends++ })
            }
        }
        compose.onNodeWithText("어떤 대결을 할까요?").assertExists()
        compose.onNodeWithContentDescription("랜덤 대결, 배치고사 1/3, 3판을 뛰면 티어가 정해져요").performClick()
        compose.onNodeWithContentDescription("친구 대결, 카톡으로 친구를 불러 함께").performClick()
        assertEquals(1, randoms)
        assertEquals(1, friends)

        state = BattleEntrySheetState(ranked)
        compose.onNodeWithContentDescription("랜덤 대결, 골드 티어, 비슷한 페이스의 러너와 대결해요").assertExists()
        compose.onNodeWithText("배치고사", substring = true, useUnmergedTree = true).assertDoesNotExist()

        state = BattleEntrySheetState(null)
        compose.onNodeWithContentDescription("랜덤 대결").assertExists()
    }

    @Test
    fun `주간 km는 정수면 정수, 아니면 소수 한 자리다`() {
        assertEquals("12", compactKm(Distance(12_000)))
        assertEquals("12.5", compactKm(Distance(12_480)))
        assertEquals("0", compactKm(Distance.ZERO))
    }
}
