package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRanking
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
        compose.onNodeWithContentDescription("크루 만들기").performClick()
        compose.onNodeWithText("가입").performClick()
        compose.onNodeWithText("저녁런 클럽").performClick()
        assertEquals(listOf("invite", "create", "join:11", "open:12"), calls)
    }

    @Test
    fun `크루 찾기 - 키보드 검색 단추로도 검색한다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(loading = false, keyword = "새벽"),
                    CrewFindActions(onSearch = { calls += "search" })
                )
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf("search"), calls)
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
    fun `크루 찾기 - 대기 중인 내 가입 신청을 보이고 취소를 묻는다(FR-143)`() {
        val request = com.a207.runkk.app.domain.crew.MyCrew.PendingJoin(91, 12, "저녁런 클럽", start)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(
                        checkingMine = false,
                        loading = false,
                        crews = listOf(crewA),
                        pending = listOf(request)
                    ),
                    CrewFindActions(onCancelAsk = { calls += "cancel:${it.requestId}" })
                )
            }
        }
        compose.onNodeWithText("승인 대기").assertExists()
        compose.onNodeWithContentDescription("저녁런 클럽 가입 신청 취소").performClick()
        assertEquals(listOf("cancel:91"), calls)
    }

    @Test
    fun `방금 만든 크루(이번 주 크루 거리 0)도 랭킹 기여율을 그린다(E2E에서 찾은 NaN 비정상 종료)`() {
        // 크루를 만든 직후: 레이스 합계 0m, 랭킹에는 나(0m)와 달빛 → 예전에는 0/0 = NaN.roundToInt()로 앱이 죽었다.
        val fresh = race.copy(total = Distance(0), contributors = emptyList(), myShare = 0.0)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 1,
                        detail = crewDetail(),
                        race = fresh,
                        ranking = CrewRanking(
                            listOf(
                                CrewRanking.Entry(1, "달빛", Distance(1_200)),
                                CrewRanking.Entry(2, "e2erunner", Distance(0))
                            )
                        )
                    ),
                    CrewHomeActions()
                )
            }
        }
        // 레이스 합계가 0이면 랭킹 합(1.2km) 대비로 나눈다.
        compose.onNodeWithContentDescription("1위 달빛 1.2킬로미터, 100%").assertExists()
        compose.onNodeWithContentDescription("2위 e2erunner", substring = true).assertExists()
    }

    @Test
    fun `내 크루 - 탭 전환과 랭킹 줄을 읽어 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        race = race,
                        ranking = ranking,
                        members = members
                    ),
                    CrewHomeActions(onTab = { calls += it.name })
                )
            }
        }
        compose.onNodeWithContentDescription("1위 바람처럼 18.2킬로미터, 31%").assertExists()
        compose.onNodeWithText("6/10명 · 크루원").assertExists()
        compose.onNodeWithText("응원보드").performClick()
        assertEquals(listOf("CHEER"), calls)
    }

    @Test
    fun `내 크루 - 크루 관리 메뉴는 크루장에게만 보이고 탭 자리에서는 뒤로 단추가 없다`() {
        var state by androidx.compose.runtime.mutableStateOf(CrewHomeUiState(crewId = 11, detail = crewDetail()))
        compose.setContent {
            RunkkTheme(reduceMotion = true) { CrewHomeScreen(state, CrewHomeActions(), atTabRoot = true) }
        }
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루 관리").assertDoesNotExist()
        compose.onNodeWithText("크루 나가기").assertExists()
        state = CrewHomeUiState(crewId = 11, detail = crewDetail(role = com.a207.runkk.app.domain.crew.CrewRole.LEADER))
        compose.waitForIdle()
        compose.onNodeWithText("크루 관리").assertExists()
    }

    @Test
    fun `크루런 탭 - 갈래요·대기실`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.CREW_RUN,
                        crewRuns = crewRunPosts
                    ),
                    CrewHomeActions(
                        onVote = { post, answer -> calls += "${post.postId}:$answer" },
                        onLobby = { calls += "lobby:${it.postId}" },
                        onResult = { calls += "result:${it.postId}" }
                    )
                )
            }
        }
        compose.onNodeWithText("같이 달려요").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("· 갈래요 2명", substring = true))[0].assertExists()
        compose.onNodeWithText("참여 투표 중").assertExists()
        compose.onAllNodesWithTextFirst("갈래요")
        compose.onAllNodesWithTextFirst("대기실 들어가기")
        // 끝난 크루런(32)은 투표·대기실 대신 내 응답과 결과 단추만 보인다.
        compose.onNodeWithText("내 응답: 갈래요").assertExists()
        compose.onNodeWithText("지난 결과").performClick()
        assertEquals(listOf("31:${VoteAnswer.GOING}", "lobby:31", "result:32"), calls)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))[0].performClick()

    @Test
    fun `크루런 진행 - 내 줄은 "나"로 읽어 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(
                        postId = 31,
                        distance = Distance(5_000),
                        phase = CrewRunPhase.RUNNING,
                        relay = listOf(RelayRow("바람처럼", Distance(2_900), 1, "CONNECTED"), myRelayRow)
                    ),
                    CrewRunActions()
                )
            }
        }
        compose.onNodeWithContentDescription("2위, 나, 새벽곰, 2.64킬로미터").assertExists()
    }

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

/** 중계의 내 줄. */
private val myRelayRow = RelayRow("새벽곰", Distance(2_640), 2, "CONNECTED", me = true)

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
        detail = crewDetail(),
        race = race.copy(
            contributors = race.contributors.map {
                if (it.displayName ==
                    "나"
                ) {
                    it.copy(me = true)
                } else {
                    it
                }
            }
        ),
        ranking = ranking,
        crewRuns = crewRunPosts.copy(
            posts = crewRunPosts.posts.map {
                if (it.postId == 31L) it.copy(crewRun = it.crewRun?.copy(myAnswer = VoteAnswer.GOING)) else it
            }
        ),
        cheers = cheers,
        members = members.copy(members = members.members.map { if (it.memberId == 2L) it.copy(me = true) else it })
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
                    myRelayRow,
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
