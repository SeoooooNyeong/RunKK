package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.VoteAnswer
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 크루 화면: 주요 버튼과 접근성 라벨. */
@RunWith(RobolectricTestRunner::class)
class CrewScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    @Test
    fun `크루 찾기 - 초대 코드·만들기·크루 열기·추천 가입`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(loading = false, crews = listOf(crewA, crewB), recommendations = listOf(crewA)),
                    CrewFindActions(
                        onInviteOpen = { calls += "invite" },
                        onCreate = { calls += "create" },
                        onOpen = { calls += "open:${it.crewId}" },
                        onJoin = { calls += "join:${it.crewId}" }
                    )
                )
            }
        }
        compose.onNodeWithText("초대 코드").performClick()
        compose.onNodeWithText("+ 만들기").performClick()
        compose.onNodeWithText("가입").performClick()
        compose.onNodeWithText("저녁런 클럽").performClick()
        assertEquals(listOf("invite", "create", "join:11", "open:12"), calls)
    }

    @Test
    fun `크루 상세 - 정원이 차면 가입할 수 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { CrewDetailContent(crewB, joining = false, onJoin = {}) }
        }
        compose.onNodeWithText("정원이 찼어요").assertIsNotEnabled()
        compose.onNodeWithText("정원은 크루장 포함 10명이에요. 크루를 나가면 24시간 동안 다른 크루에 가입할 수 없어요.").assertExists()
    }

    @Test
    fun `내 크루 - 탭 전환과 랭킹 줄을 읽어 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(crewId = 11, crewName = "새벽질주", race = race, ranking = ranking, members = members),
                    CrewHomeActions(onTab = { calls += it.name })
                )
            }
        }
        compose.onNodeWithContentDescription("1위 바람처럼 18.2킬로미터, 31%").assertExists()
        compose.onNodeWithText("응원보드").performClick()
        assertEquals(listOf("CHEER"), calls)
    }

    @Test
    fun `크루런 탭 - 갈래요·대기실`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(crewId = 11, crewName = "새벽질주", tab = CrewTab.CREW_RUN, crewRuns = crewRunPosts),
                    CrewHomeActions(
                        onVote = { post, answer -> calls += "${post.postId}:$answer" },
                        onLobby = { calls += "lobby:${it.postId}" }
                    )
                )
            }
        }
        compose.onNodeWithText("토 07:00 · 5km 같이 달려요").assertExists()
        compose.onAllNodesWithTextFirst("갈래요")
        compose.onAllNodesWithTextFirst("대기실 들어가기")
        assertEquals(listOf("31:${VoteAnswer.GOING}", "lobby:31"), calls)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))[0].performClick()

    @Test
    fun `크루런 진행 - 그만두기는 길게 눌러 확인`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(postId = 31, distance = Distance(5_000), phase = CrewRunPhase.RUNNING),
                    CrewRunActions(onQuitTap = { calls += "tap" }, onQuitLongPress = { calls += "long" })
                )
            }
        }
        val quit = compose.onNodeWithContentDescription("크루런 그만두기")
        quit.performClick()
        quit.performTouchInput { longClick() }
        assertEquals(listOf("tap", "long"), calls)
    }
}

/** 크루 스크린샷(G1~G8). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun find() = compose.captureRunkkScreen("G1_crew_find") {
        CrewFindScreen(
            CrewFindUiState(
                loading = false,
                tags = setOf("SOCIAL_RUN"),
                crews = listOf(crewA, crewB, crewC),
                recommendations = listOf(crewA)
            ),
            CrewFindActions()
        )
    }

    @Test
    fun detail() = compose.captureRunkkScreen("G1_crew_detail_sheet") {
        Box(Modifier.fillMaxSize().background(RunkkColors.Surface)) {
            CrewDetailContent(crewC, joining = false, onJoin = {})
        }
    }

    @Test
    fun create() = compose.captureRunkkScreen("G2_crew_create") {
        CrewCreateScreen(
            CrewCreateUiState(
                CrewFormState(
                    name = "새벽질주",
                    intro = "새벽에 같이 달려요",
                    tags = setOf("SOCIAL_RUN", "DAWN", "BEGINNER_WELCOME")
                )
            ),
            {},
            {},
            {}
        )
    }

    private val home = CrewHomeUiState(
        crewId = 11,
        crewName = "새벽질주",
        race = race,
        ranking = ranking,
        crewRuns = crewRunPosts,
        cheers = cheers,
        members = members,
        votes = mapOf(31L to VoteAnswer.GOING)
    )

    @Test
    fun race() = compose.captureRunkkScreen("G3_my_crew_race") { CrewHomeScreen(home, CrewHomeActions()) }

    @Test
    fun crewRuns() = compose.captureRunkkScreen("G3_my_crew_crew_runs") {
        CrewHomeScreen(home.copy(tab = CrewTab.CREW_RUN), CrewHomeActions())
    }

    @Test
    fun cheerBoard() = compose.captureRunkkScreen("G3_my_crew_cheer_board") {
        CrewHomeScreen(home.copy(tab = CrewTab.CHEER, cheerDraft = "이번 주도 화이팅"), CrewHomeActions())
    }

    @Test
    fun memberList() = compose.captureRunkkScreen("G3_my_crew_members") {
        CrewHomeScreen(home.copy(tab = CrewTab.MEMBERS), CrewHomeActions())
    }

    @Test
    fun manage() = compose.captureRunkkScreen("G4_crew_manage") {
        CrewManageScreen(
            CrewManageUiState(
                crewId = 11,
                crewName = "새벽질주",
                loading = false,
                requests = com.a207.runkk.app.domain.crew.PendingRequests(
                    listOf(
                        com.a207.runkk.app.domain.crew.PendingRequests.Request(91, 9, "새내기", start.minusSeconds(86_400))
                    )
                ),
                members = members,
                inviteCode = "K7P2QX",
                form = CrewFormState(name = "새벽질주")
            ),
            CrewManageActions()
        )
    }

    @Test
    fun write() = compose.captureRunkkScreen("G5_crew_run_write") {
        CrewRunWriteScreen(
            CrewRunWriteUiState(title = "토요일 아침 5K", today = LocalDate.of(2026, 10, 20), dayOffset = 4),
            {},
            {},
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun lobbyScreen() = compose.captureRunkkScreen("G6_crew_run_lobby") {
        CrewRunScreen(
            CrewRunUiState(
                postId = 31,
                distance = Distance(5_000),
                phase = CrewRunPhase.LOBBY,
                lobby = lobby,
                ready = true,
                untilStart = Duration.ofSeconds(512)
            ),
            CrewRunActions()
        )
    }

    @Test
    fun countdown() = compose.captureRunkkScreen("G6_crew_run_countdown") {
        CrewRunScreen(
            CrewRunUiState(postId = 31, distance = Distance(5_000), phase = CrewRunPhase.COUNTDOWN, countdown = 2),
            CrewRunActions()
        )
    }

    @Test
    fun relay() = compose.captureRunkkScreen("G7_crew_run_relay") {
        CrewRunScreen(
            CrewRunUiState(
                postId = 31,
                distance = Distance(5_000),
                phase = CrewRunPhase.RUNNING,
                relay = listOf(
                    RelayRow("바람처럼", Distance(2_900), 1, "CONNECTED"),
                    RelayRow("나", Distance(2_640), 2, "CONNECTED"),
                    RelayRow("달빛", Distance(1_980), 3, "CHECKING"),
                    RelayRow("거북", Distance(800), null, "QUIT")
                )
            ),
            CrewRunActions()
        )
    }

    @Test
    fun result() = compose.captureRunkkScreen("G8_crew_run_result") {
        CrewRunResultScreen(CrewRunResultUiState(loading = false, result = crewRunResult), {}, {}, {})
    }
}
