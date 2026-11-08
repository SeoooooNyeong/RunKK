package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PostList
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
    fun `크루 찾기 - 초대 코드·만들기·크루 열기, 추천 영역은 없다(FR-118 삭제)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(isLoading = false, crews = listOf(crewA, crewB)),
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
        compose.onNodeWithText("크루 만들기").performClick()
        compose.onNodeWithText("저녁런 클럽").performClick()
        compose.onNodeWithText("FOR YOU", substring = true).assertDoesNotExist()
        compose.onNodeWithText("태그로 찾기").assertExists()
        compose.onNodeWithText("공개 크루 2개, 최근 생성순").assertExists()
        assertEquals(listOf("invite", "create", "open:12"), calls)
    }

    @Test
    fun `크루 찾기 - 키보드 검색 단추로도 검색한다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(isLoading = false, keyword = "새벽"),
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
            RunkkTheme(reduceMotion = true) { CrewDetailContent(crewB, isJoining = false, onJoin = {}) }
        }
        compose.onNodeWithText("정원이 찼어요").assertIsNotEnabled()
        compose.onNodeWithText("10/10명, 승인 후 가입").assertExists()
    }

    @Test
    fun `크루 정보 - 신청을 보낸 크루는 결정 기한과 신청 취소를 보인다(와이어 I1, FR-121)`() {
        val request = com.a207.runkk.app.domain.crew.MyCrew.PendingJoin(91, 12, "저녁런 클럽", start)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(
                        isCheckingMine = false,
                        isLoading = false,
                        detail = crewB,
                        pending = listOf(request)
                    ),
                    CrewFindActions(onCancelAsk = { calls += "cancel:${it.requestId}" }, onCloseDetail = {
                        calls +=
                            "close"
                    })
                )
            }
        }
        compose.onNodeWithText("크루 정보").assertExists()
        compose.onNodeWithText("가입 신청을 보냈어요").assertExists()
        compose.onNodeWithText("10.27까지 결정돼요").assertExists()
        compose.onNodeWithText("신청 취소").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(listOf("cancel:91", "close"), calls)
    }

    @Test
    fun `초대 코드 - 여섯 글자를 채워야 가입하기를 누른다(와이어 E5)`() {
        var code by androidx.compose.runtime.mutableStateOf("A7K2")
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(isCheckingMine = false, isLoading = false, isInviteOpen = true, inviteCode = code),
                    CrewFindActions(onInviteSubmit = { calls += "submit" })
                )
            }
        }
        compose.onNodeWithText("초대 코드를 입력해 주세요").assertExists()
        compose.onNodeWithText("가입하기").assertIsNotEnabled()
        code = "A7K2Q9"
        compose.waitForIdle()
        compose.onNodeWithText("가입하기").performClick()
        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `크루 찾기 - 대기 중인 내 가입 신청을 보이고 취소를 묻는다(FR-143)`() {
        val request = com.a207.runkk.app.domain.crew.MyCrew.PendingJoin(91, 12, "저녁런 클럽", start)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    CrewFindUiState(
                        isCheckingMine = false,
                        isLoading = false,
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
    fun `방금 만든 크루(이번 주 크루 거리 0)도 월간 기여도 보드를 그린다(E2E에서 찾은 NaN 비정상 종료)`() {
        // 크루를 만든 직후: 레이스 합계 0m, 랭킹에는 나(0m)와 달빛 → 예전에는 0/0 = NaN.roundToInt()로 앱이 죽었다.
        val fresh = race.copy(total = Distance(0), contributors = emptyList(), myShare = 0.0)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 1,
                        detail = crewDetail(),
                        race = fresh,
                        period = CrewRanking.Period.MONTHLY,
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
        // 순위 숫자 없이 이름과 거리만 읽는다(TC-7497).
        compose.onNodeWithContentDescription("달빛 1.2킬로미터").assertExists()
        compose.onNodeWithContentDescription("e2erunner 0.0킬로미터").assertExists()
    }

    @Test
    fun `내 크루 - 탭 다섯 칸 전환과 기여도 줄을 읽어 준다(와이어 E2)`() {
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
                    CrewHomeActions(onTab = { calls += it.name }, onChat = { calls += "chat" })
                )
            }
        }
        compose.onNodeWithContentDescription("바람처럼 18.2킬로미터").assertExists()
        compose.onNodeWithText("6/10명, 함께가 좋아요, 새벽").assertExists()
        compose.onNodeWithText("응원").assertDoesNotExist()
        compose.onNodeWithText("게시판").performClick()
        compose.onNodeWithText("채팅").performClick()
        compose.onNodeWithText("크루원").performClick()
        assertEquals(listOf("BOARD", "chat", "MEMBERS"), calls)
    }

    @Test
    fun `내 크루 - 크루 메뉴(⋯)는 크루장, 운영진에게만 보이고 크루 관리로 간다, 탭 자리에서는 뒤로 단추가 없다`() {
        var state by androidx.compose.runtime.mutableStateOf(CrewHomeUiState(crewId = 11, detail = crewDetail()))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(state, CrewHomeActions(onManage = { calls += "manage" }), isAtTabRoot = true)
            }
        }
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").assertDoesNotExist()
        state = CrewHomeUiState(crewId = 11, detail = crewDetail(role = com.a207.runkk.app.domain.crew.CrewRole.LEADER))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        assertEquals(listOf("manage"), calls)
    }

    @Test
    fun `일정 탭 - 크루런 참석·불참·미정과 대기실(FR-145)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.SCHEDULE,
                        crewRuns = crewRunPosts,
                        lobbyEntries = mapOf(31L to LobbyAvailability.Open, 32L to LobbyAvailability.Closed)
                    ),
                    CrewHomeActions(
                        onAttendance = { post, answer -> calls += "${post.postId}:$answer" },
                        onLobby = { calls += "lobby:${it.postId}" },
                        onResult = { calls += "result:${it.postId}" }
                    )
                )
            }
        }
        compose.onNodeWithText("같이 달려요").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("크루런, 비대면", substring = true))[0].assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("참석 2", substring = true))[0].assertExists()
        compose.onAllNodesWithTextFirst("참석")
        compose.onAllNodesWithTextFirst("대기실 들어가기")
        // 끝난 크루런(32)은 지난 일정에 있고 누르면 결과로 간다(와이어 I4).
        compose.onNodeWithText("지난 일정").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("지난 결과", substring = true).performScrollTo().performClick()
        assertEquals(listOf("31:${AttendanceAnswer.GOING}", "lobby:31", "result:32"), calls)
    }

    private fun showCrewRuns(entry: LobbyAvailability, status: CrewRunStatus = CrewRunStatus.VOTING) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.SCHEDULE,
                        crewRuns = PostList(
                            listOf(
                                crewRunPosts.posts[0].copy(crewRun = crewRunVoting.copy(status = status))
                            )
                        ),
                        lobbyEntries = mapOf(31L to entry)
                    ),
                    CrewHomeActions(onLobby = { calls += "lobby:${it.postId}" })
                )
            }
        }
    }

    @Test
    fun `크루런 탭 - 대기실이 열리기 전에는 남은 분을 보이고 누를 수 없다(FR-133)`() {
        showCrewRuns(LobbyAvailability.NotYet(10))
        compose.onNodeWithText("대기실 들어가기").assertDoesNotExist()
        compose.onNodeWithText("대기실 10분 뒤 열려요").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `크루런 탭 - 한 시간보다 많이 남았으면 30분 전에 열린다고 안내한다`() {
        showCrewRuns(LobbyAvailability.NotYet(61))
        compose.onNodeWithText("대기실은 시작 30분 전에 열려요").assertIsNotEnabled()
    }

    @Test
    fun `크루런 탭 - 30분 전부터는 대기실에 들어간다`() {
        showCrewRuns(LobbyAvailability.Open, CrewRunStatus.LOBBY_OPEN)
        compose.onNodeWithText("대기실 들어가기").assertIsEnabled().performClick()
        assertEquals(listOf("lobby:31"), calls)
    }

    @Test
    fun `크루런 탭 - 모집 일시가 지났으면 대기실이 닫혔다고 보이고 누를 수 없다`() {
        showCrewRuns(LobbyAvailability.Closed, CrewRunStatus.RUNNING)
        compose.onNodeWithText("대기실 들어가기").assertDoesNotExist()
        compose.onNodeWithText("대기실이 닫혔어요").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `크루런 대기실 - 열리지 않아 못 들어가면 돌아가기·뒤로 단추·시스템 뒤로 가기로 나간다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(
                        postId = 31,
                        error = UiText.Res(R.string.crew_error_lobby_not_open),
                        canRetry = false
                    ),
                    CrewRunActions(onBack = { calls += "back" }, onRefresh = { calls += "retry" })
                )
            }
        }
        compose.onNodeWithText("대기실은 시작 30분 전부터 시작 시각까지만 열려요").assertExists()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("back", "back", "back"), calls)
    }

    @Test
    fun `크루런 대기실 - 네트워크 오류면 다시 시도와 뒤로 단추를 함께 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(postId = 31, error = UiText.Res(R.string.crew_run_lobby_until_checking)),
                    CrewRunActions(onBack = { calls += "back" }, onRefresh = { calls += "retry" })
                )
            }
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(listOf("retry", "back"), calls)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))[0].performClick()

    @Test
    fun `크루런 진행 - 함께 달리는 크루원 수(나·끝낸 사람 빼고)와 내 거리·지표를 보인다(F-15)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(runningState(), CrewRunActions())
            }
        }
        compose.onNodeWithText("함께 달리는 크루원 2명").assertExists()
        compose.onNodeWithContentDescription("3.22킬로미터").assertExists()
        compose.onNodeWithText("크루런 · 5km").assertExists()
    }

    @Test
    fun `크루런 진행 - 길게 눌러 종료는 그만두기 확인을, 일시정지는 정지를 부른다(F-15·F-15d)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    runningState(),
                    CrewRunActions(
                        onQuitTap = { calls += "tap" },
                        onQuitLongPress = { calls += "long" },
                        onPause = { calls += "pause" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription("크루런 그만두기").performClick()
        compose.onNodeWithContentDescription("크루런 그만두기").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("일시정지").performClick()
        assertEquals(listOf("tap", "long", "pause"), calls)
    }

    @Test
    fun `크루런 일시정지 - 재개 단추를 보인다(F-15c)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    runningState().copy(mine = runningState().mine.copy(isPaused = true)),
                    CrewRunActions(onResume = { calls += "resume" })
                )
            }
        }
        compose.onNodeWithContentDescription("재개").performClick()
        assertEquals(listOf("resume"), calls)
    }

    @Test
    fun `크루런 대기실 - 미입장·대기 중·준비 완료와 준비 수를 보이고 나가기를 부른다(F-14)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(
                        postId = 31,
                        distance = Distance(5_000),
                        phase = CrewRunPhase.LOBBY,
                        lobby = lobbyWithAbsent,
                        untilStart = Duration.ofSeconds(754)
                    ),
                    CrewRunActions(onLeave = { calls += "leave" })
                )
            }
        }
        compose.onNodeWithText("준비 완료 1 / 4").assertExists()
        compose.onNodeWithText("미입장").assertExists()
        compose.onNodeWithText("12:34", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("나가기").performClick()
        assertEquals(listOf("leave"), calls)
    }
}

/** 미입장 한 명을 더한 대기실(F-14b). */
private val lobbyWithAbsent = lobby.copy(
    participants =
        lobby.participants + Lobby.Participant("거북", false, 3, isEntered = false)
)

/** 진행 중(F-15): 나 3.22km, 크루원 셋(멈춤 하나, 끝냄 하나). */
private fun runningState() = CrewRunUiState(
    postId = 31,
    distance = Distance(5_000),
    phase = CrewRunPhase.RUNNING,
    relay = listOf(
        RelayRow("바람처럼", Distance(3_400)),
        RelayRow("새벽곰", Distance(3_220), isMe = true),
        RelayRow("달빛", Distance(2_980), MateState.PAUSED),
        RelayRow("거북", Distance(1_200), MateState.ENDED)
    ),
    totalDistance = Distance(10_800),
    mine = CrewRunProgress(
        distance = Distance(3_220),
        elapsed = Duration.ofSeconds(1_125),
        currentPace = Pace(324),
        averagePace = Pace(331)
    )
)

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
                isLoading = false,
                tags = setOf("SOCIAL_RUN"),
                crews = listOf(crewA, crewB, crewC)
            ),
            CrewFindActions()
        )
    }

    @Test
    fun detail() = compose.captureRunkkScreen("G1_crew_detail") {
        CrewFindScreen(CrewFindUiState(isCheckingMine = false, isLoading = false, detail = crewC), CrewFindActions())
    }

    @Test
    fun invite() = compose.captureRunkkScreen("G1_crew_invite_code") {
        CrewFindScreen(
            CrewFindUiState(isCheckingMine = false, isLoading = false, isInviteOpen = true, inviteCode = "A7K2Q"),
            CrewFindActions()
        )
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
                    it.copy(isMe = true)
                } else {
                    it
                }
            }
        ),
        ranking = ranking,
        crewRuns = crewRunPosts.copy(
            posts = crewRunPosts.posts.map {
                if (it.postId == 31L) it.copy(crewRun = it.crewRun?.copy(myAnswer = AttendanceAnswer.GOING)) else it
            }
        ),
        lobbyEntries = mapOf(31L to LobbyAvailability.of(crewRunVoting, start)),
        members = members.copy(members = members.members.map { if (it.memberId == 2L) it.copy(isMe = true) else it })
    )

    @Test
    fun race() = compose.captureRunkkScreen("G3_my_crew_race") { CrewHomeScreen(home, CrewHomeActions()) }

    @Test
    fun crewRuns() = compose.captureRunkkScreen("G3_my_crew_crew_runs") {
        CrewHomeScreen(home.copy(tab = CrewTab.SCHEDULE), CrewHomeActions())
    }

    @Test
    fun `크루원 탭은 내 정보, 티어와 한줄 소개, 크루 탈퇴를 보인다(와이어 E4)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(home.copy(tab = CrewTab.MEMBERS), CrewHomeActions())
            }
        }
        compose.onNodeWithText("크루에서 보이는 내 정보").assertExists()
        compose.onNodeWithText("골드, 매일 새벽 5시").assertExists()
        compose.onNodeWithText("크루원 3").assertExists()
        compose.onNodeWithText("MORNING_SPARROW").assertDoesNotExist()
        compose.onNodeWithText("크루 탈퇴").performScrollTo().assertExists()
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
                detail = crewDetail(role = com.a207.runkk.app.domain.crew.CrewRole.LEADER),
                isLoading = false,
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
                lobby = lobbyWithAbsent,
                isReady = true,
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
        CrewRunScreen(runningState(), CrewRunActions())
    }

    @Test
    fun paused() = compose.captureRunkkScreen("G7_crew_run_paused") {
        CrewRunScreen(runningState().copy(mine = runningState().mine.copy(isPaused = true)), CrewRunActions())
    }

    @Test
    fun quitConfirm() = compose.captureRunkkScreen("G7_crew_run_quit_confirm") {
        CrewRunScreen(runningState().copy(isConfirmingQuit = true), CrewRunActions())
    }

    @Test
    fun result() = compose.captureRunkkScreen("G8_crew_run_result") {
        CrewRunResultScreen(CrewRunResultUiState(isLoading = false, result = crewRunResult), {}, {}, {})
    }

    @Test
    fun resultDetailWaiting() = compose.captureRunkkScreen("G8_crew_run_result_detail") {
        CrewRunResultScreen(
            CrewRunResultUiState(isLoading = false, result = crewRunResult),
            {},
            {},
            {},
            pagerState = androidx.compose.foundation.pager.PagerState(currentPage = 1) { 2 }
        )
    }
}
