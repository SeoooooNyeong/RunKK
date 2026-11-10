package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 크루런 피그마 맞춤 W2(S15P31A207-293, F-14~F-16b): 대기실 얼굴 원, 제한 없음, 지도 이름표, 결과 상세 머리 줄. */
@RunWith(RobolectricTestRunner::class)
class CrewRunFigmaW2Test {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val mixedLobby = lobby.copy(
        participants = lobby.participants + Lobby.Participant("들바람", false, 9, isEntered = false)
    )

    private fun show(state: CrewRunUiState) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    state,
                    CrewRunActions(onReady = {
                        calls += "ready"
                    }, onQuitConfirm = { calls += "quit" })
                )
            }
        }
    }

    @Test
    fun `대기실 참가자 원은 들어온 사람은 얼굴, 미입장은 점선 빈 원이다(F-14, D-성-12)`() {
        show(CrewRunUiState(postId = 31, distance = Distance(5_000), phase = CrewRunPhase.LOBBY, lobby = mixedLobby))
        compose.onAllNodesWithTag(LOBBY_EMPTY_SEAT_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(LOBBY_FACE_SEAT_TAG, useUnmergedTree = true).assertCountEquals(3)
        compose.onNodeWithText("미입장", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `칩은 가운뎃점 없이 크루런과 거리이고 화면 어디에도 가운뎃점이 없다(F-14)`() {
        show(CrewRunUiState(postId = 31, distance = Distance(5_000), phase = CrewRunPhase.LOBBY, lobby = lobby))
        compose.onNodeWithText("크루런 5km", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasText("·", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `거리가 없는 크루런은 제한 없음으로 보이고 준비 완료를 누를 수 있다(D-성-28)`() {
        show(CrewRunUiState(postId = 31, distance = null, phase = CrewRunPhase.LOBBY, lobby = lobby))
        compose.onNodeWithText("크루런 제한 없음", useUnmergedTree = true).assertExists()
        compose.onNode(hasText("준비 완료") and hasClickAction()).performClick()
        assertEquals(listOf("ready"), calls)
    }

    @Test
    fun `취소 대기실은 남은 시간을 0시 0분 형식으로 보인다(F-14c)`() {
        show(
            CrewRunUiState(
                postId = 31,
                distance = Distance(5_000),
                phase = CrewRunPhase.CANCELLED,
                lobby = lobby,
                untilStart = Duration.ZERO,
                cancelledReason = "취소"
            )
        )
        compose.onNodeWithText("0:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `진행 지도의 달리는 크루원은 이름표가 없고 멈춘 크루원만 멈춤 표시다(F-15, D-성-13)`() {
        show(
            runningState()
        )
        compose.onNodeWithText("바람처럼", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("달빛 멈춤", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("나", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `그만두기 확인은 대화상자 제목과 계속 달리기, 그만두기다(F-15d)`() {
        show(runningState().copy(isConfirmingQuit = true))
        compose.onNodeWithText("크루런을 그만둘까요?").assertExists()
        compose.onNodeWithText("계속 달리기").assertExists()
        compose.onNodeWithText("그만두기").performClick()
        assertEquals(listOf("quit"), calls)
    }

    @Test
    fun `결과 첫 쪽은 뒤로 버튼이 있고 상세 쪽은 뒤로 버튼이 없다(F-16, F-16b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunResultScreen(
                    CrewRunResultUiState(isLoading = false, result = crewRunResult),
                    onCard = {},
                    onHome = {},
                    onRetry = {},
                    pagerState = rememberPagerState { 2 }
                )
            }
        }
        compose.onNodeWithContentDescription("뒤로").assertExists()
        compose.onNodeWithText("밀어서 상세 결과 보기 ›").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
    }
}

/** 크루런 W2 스크린샷: 경로 위 크루원(달리는 중·멈춤)과 일시정지, 얼굴·점선 원 대기실. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewRunFigmaW2ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun relayRoute() = compose.captureRunkkScreen("G7_crew_run_relay_route_light", isDarkTheme = false) {
        CrewRunScreen(runningState(), CrewRunActions())
    }

    @Test
    fun pausedRoute() = compose.captureRunkkScreen("G7_crew_run_paused_route_light", isDarkTheme = false) {
        CrewRunScreen(runningState(isPaused = true), CrewRunActions())
    }

    @Test
    fun lobbySeats() = compose.captureRunkkScreen("G6_crew_run_lobby_seats_light", isDarkTheme = false) {
        CrewRunScreen(
            CrewRunUiState(
                postId = 31,
                distance = null,
                phase = CrewRunPhase.LOBBY,
                lobby = lobby.copy(
                    participants = lobby.participants + Lobby.Participant("들바람", false, 9, isEntered = false)
                ),
                untilStart = Duration.ofSeconds(754)
            ),
            CrewRunActions()
        )
    }
}

/** 북동쪽으로 휘는 짧은 경로(진행 지도). */
private val route = RouteLine(
    listOf(
        GeoPoint(37.5000, 127.0000),
        GeoPoint(37.5006, 127.0004),
        GeoPoint(37.5012, 127.0012),
        GeoPoint(37.5016, 127.0024),
        GeoPoint(37.5024, 127.0031)
    )
)

/** 진행 중(F-15): 나 0.40km, 앞선 크루원 하나, 멈춘 크루원 하나. */
private fun runningState(isPaused: Boolean = false) = CrewRunUiState(
    postId = 31,
    distance = Distance(5_000),
    phase = CrewRunPhase.RUNNING,
    relay = listOf(
        RelayRow("나", Distance(400), isMe = true),
        RelayRow("바람처럼", Distance(320)),
        RelayRow("달빛", Distance(250), MateState.PAUSED)
    ),
    mine = CrewRunProgress(
        distance = Distance(400),
        elapsed = Duration.ofSeconds(1_125),
        currentPace = Pace(324),
        averagePace = Pace(331),
        isPaused = isPaused,
        route = route
    )
)
