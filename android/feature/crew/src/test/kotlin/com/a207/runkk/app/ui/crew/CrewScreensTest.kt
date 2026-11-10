package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
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
import kotlin.test.assertNull
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
                        onSearchOpen = { calls += "search" },
                        onOpen = { calls += "open:${it.crewId}" },
                        onJoin = { calls += "join:${it.crewId}" }
                    )
                )
            }
        }
        compose.onNodeWithText("초대 코드").performClick()
        compose.onNodeWithText("크루 만들기").performClick()
        compose.onNodeWithContentDescription("크루 검색 열기").performClick()
        compose.onNodeWithText("저녁런 클럽").performClick()
        compose.onNodeWithText("FOR YOU", substring = true).assertDoesNotExist()
        compose.onNodeWithText("조건 설정하기").assertExists()
        compose.onNodeWithText("전체 크루").assertExists()
        assertEquals(listOf("invite", "create", "search", "open:12"), calls)
    }

    @Test
    fun `크루 검색 - 키보드 검색 단추로 찾고 결과 수는 서버 전체 수, 지우기로 비운다(F-01d)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewSearchScreen(
                    CrewSearchUiState(keyword = "새벽", hasSearched = true, results = listOf(crewA), resultCount = 3),
                    CrewSearchActions(onSearch = { calls += "search" }, onClear = { calls += "clear" })
                )
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("검색 결과 3개").assertExists()
        compose.onNodeWithContentDescription("검색어 지우기").performClick()
        assertEquals(listOf("search", "clear"), calls)
    }

    @Test
    fun `크루 검색 - 결과가 없으면 안내만 보인다(F-01e)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewSearchScreen(CrewSearchUiState(keyword = "없는", hasSearched = true), CrewSearchActions())
            }
        }
        compose.onNodeWithText("검색 결과 0개").assertExists()
        compose.onNodeWithText("검색 결과가 없어요").assertExists()
    }

    @Test
    fun `크루 상세 - 정원이 차면 가입할 수 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { CrewDetailContent(crewB, isJoining = false, onJoin = {}) }
        }
        compose.onNodeWithText("정원이 찼어요").assertIsNotEnabled()
        compose.onNodeWithContentDescription("10명", substring = true).assertExists()
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
    fun `초대 코드 - 여섯 글자를 채워야 확인(조회)을 누르고, 찾으면 미리보기와 가입하기(F-03·F-03c)`() {
        var invite by androidx.compose.runtime.mutableStateOf(InviteSheetState(code = "A7K2"))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InviteSheetContent(
                    invite,
                    CrewFindActions(onInviteConfirm = { calls += "confirm" }, onInviteSubmit = { calls += "submit" })
                )
            }
        }
        compose.onNodeWithText("확인").assertIsNotEnabled()
        invite = InviteSheetState(code = "A7K2Q9")
        compose.waitForIdle()
        compose.onNodeWithText("확인").performClick()
        invite = invite.copy(step = InviteStep.FOUND, preview = invitePreview)
        compose.waitForIdle()
        compose.onNodeWithText("새벽러너스").assertExists()
        compose.onNodeWithText("가입하기").performClick()
        assertEquals(listOf("confirm", "submit"), calls)
    }

    @Test
    fun `초대 코드 - 맞는 크루가 없으면 칸 아래 안내를 보인다(F-03b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                InviteSheetContent(InviteSheetState(code = "ZZZZZZ", step = InviteStep.NOT_FOUND), CrewFindActions())
            }
        }
        compose.onNodeWithText("일치하는 크루가 없어요. 초대 코드를 다시 확인해 주세요.").assertExists()
        compose.onNodeWithText("확인").assertIsEnabled()
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
        compose.onNodeWithText("가입 신청 대기 중", substring = true).assertExists()
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
    fun `내 크루 - 탭 세 칸 전환과 기여도 줄을 읽어 준다(피그마 F-05)`() {
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
        compose.onNodeWithContentDescription("바람처럼 18.2킬로미터").assertExists()
        compose.onNodeWithText("응원").assertDoesNotExist()
        compose.onNodeWithText("게시판").assertDoesNotExist()
        compose.onNodeWithText("크루런").performClick()
        compose.onNodeWithText("채팅").performClick()
        assertEquals(listOf("SCHEDULE", "CHAT"), calls, "채팅도 탭이다(D-성-21)")
    }

    @Test
    fun `내 크루 - 크루장의 ⋯ 메뉴도 크루 정보·크루원 두 줄뿐이고 탭 자리에서는 뒤로 단추가 없다(F-24, W7)`() {
        val leader = crewDetail(role = com.a207.runkk.app.domain.crew.CrewRole.LEADER)
        var state by androidx.compose.runtime.mutableStateOf(CrewHomeUiState(crewId = 11, detail = leader))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    state,
                    CrewHomeActions(
                        onMenuOpen = {
                            calls += "menu"
                            state = state.copy(isMenuOpen = true)
                        },
                        onPage = { calls += "page:$it" }
                    ),
                    isAtTabRoot = true
                )
            }
        }
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루 관리").assertDoesNotExist()
        compose.onNodeWithText("크루 정보").performClick()
        assertEquals(listOf("menu", "page:INFO"), calls)
    }

    @Test
    fun `일정 탭 - 가장 가까운 크루런은 강조 카드이고 제목은 일정 상세, 지난 크루런 링크는 늘 보인다(F-11, W6)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.SCHEDULE,
                        crewRuns = crewRunPosts,
                        scheduleNow = crewRunVoting.scheduledAt.minusSeconds(1_785),
                        lobbyEntries = mapOf(31L to LobbyAvailability.Open, 32L to LobbyAvailability.Closed)
                    ),
                    CrewHomeActions(
                        onLobby = { calls += "lobby:${it.postId}" },
                        onRunOpen = { calls += "open:${it.postId}" },
                        onPastRuns = { calls += "past" },
                        onWriteSchedule = { calls += "write" }
                    )
                )
            }
        }
        compose.onNodeWithText("참석 2명").assertExists()
        compose.onNodeWithContentDescription("29분 45초 후 시작").assertExists()
        compose.onNodeWithText("대기실 입장하기").performClick()
        compose.onNodeWithText("같이 달려요").performClick()
        compose.onNodeWithText("크루런 모집하기").performClick()
        compose.onNodeWithContentDescription("지난 크루런").performClick()
        // 끝난 크루런(32)은 이 탭에 두지 않는다(지난 크루런 화면 F-11b가 보인다).
        compose.onNodeWithText("10km LSD").assertDoesNotExist()
        assertEquals(listOf("lobby:31", "open:31", "write", "past"), calls)
    }

    @Test
    fun `일정 탭 - 나머지 크루런은 이번주·다음주 날짜 줄이고 참석 칩을 누르면 참석을 켜고 끈다(F-11)`() {
        // 강조 카드는 토 10.24 07:00, 이번주는 일 10.25, 다음주는 토 10.31(주는 월요일부터).
        val now = crewRunVoting.scheduledAt.minusSeconds(1_785)
        val sat = crewRunPosts.posts[0].copy(
            postId = 33,
            summary = "토요일 한강",
            crewRun = crewRunVoting.copy(scheduledAt = now.plusSeconds(86_400), goingCount = 5)
        )
        val next = crewRunPosts.posts[0].copy(
            postId = 34,
            summary = "주말 롱런",
            crewRun = crewRunVoting.copy(
                scheduledAt = now.plusSeconds(7 * 86_400),
                goingCount = 4,
                myAnswer = AttendanceAnswer.GOING
            )
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail(),
                        tab = CrewTab.SCHEDULE,
                        crewRuns = PostList(listOf(crewRunPosts.posts[0], sat, next)),
                        scheduleNow = now
                    ),
                    CrewHomeActions(
                        onAttendance = { post, answer -> calls += "${post.postId}:$answer" },
                        onAttendanceWithdraw = { calls += "${it.postId}:withdraw" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription("이번주 1개").assertExists()
        compose.onNodeWithContentDescription("다음주 1개").assertExists()
        compose.onNodeWithContentDescription("참석 응답, 참석 5").assertIsOff().performClick()
        compose.onNodeWithContentDescription("참석 응답, 참석 4").assertIsOn().performClick()
        assertEquals(listOf("33:${AttendanceAnswer.GOING}", "34:withdraw"), calls)
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
                        lobbyEntries = mapOf(31L to entry),
                        scheduleNow = crewRunVoting.scheduledAt.minusSeconds(3_600)
                    ),
                    CrewHomeActions(onLobby = { calls += "lobby:${it.postId}" })
                )
            }
        }
    }

    @Test
    fun `크루런 탭 - 대기실이 열리기 전에는 남은 분을 보이고 누를 수 없다(FR-133)`() {
        showCrewRuns(LobbyAvailability.NotYet(10))
        compose.onNodeWithText("대기실 입장하기").assertDoesNotExist()
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
        compose.onNodeWithText("대기실 입장하기").assertIsEnabled().performClick()
        assertEquals(listOf("lobby:31"), calls)
    }

    @Test
    fun `크루런 탭 - 모집 일시가 지났으면 대기실이 닫혔다고 보이고 누를 수 없다`() {
        showCrewRuns(LobbyAvailability.Closed, CrewRunStatus.RUNNING)
        compose.onNodeWithText("대기실 입장하기").assertDoesNotExist()
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

    @Test
    fun `크루런 진행 - 함께 달리는 크루원 수(나·끝낸 사람 빼고)와 내 거리·지표를 보인다(F-15)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(runningState(), CrewRunActions())
            }
        }
        compose.onNodeWithText("함께 달리는 크루원 2명").assertExists()
        compose.onNodeWithContentDescription("3.22킬로미터").assertExists()
        compose.onNodeWithText("크루런 5km", useUnmergedTree = true).assertExists()
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
                tags = setOf("WEEKDAY_EVENING"),
                crews = listOf(crewA, crewB, crewC)
            ),
            CrewFindActions()
        )
    }

    @Test
    fun detail() = compose.captureRunkkScreen("G1_crew_detail") {
        CrewFindScreen(
            CrewFindUiState(isCheckingMine = false, isLoading = false, detail = crewC, detailInfo = crewCInfo),
            CrewFindActions()
        )
    }

    @Test
    fun detailCooldown() = compose.captureRunkkScreen("F02c_crew_detail_cooldown") {
        CrewFindScreen(
            CrewFindUiState(
                isCheckingMine = false,
                isLoading = false,
                detail = crewA,
                detailInfo = crewCInfo.copy(crewId = crewA.crewId),
                joinRemaining = Duration.ofSeconds(23 * 3600 + 59 * 60 + 12)
            ),
            CrewFindActions()
        )
    }

    @Test
    fun invite() = compose.captureRunkkScreen("G1_crew_invite_code") {
        InviteSheetSurface { InviteSheetContent(InviteSheetState(code = "A7K2Q"), CrewFindActions()) }
    }

    @Test
    fun inviteNotFound() = compose.captureRunkkScreen("F03b_crew_invite_not_found") {
        InviteSheetSurface {
            InviteSheetContent(InviteSheetState(code = "ZZZZZZ", step = InviteStep.NOT_FOUND), CrewFindActions())
        }
    }

    @Test
    fun invitePreviewFound() = compose.captureRunkkScreen("F03c_crew_invite_preview") {
        InviteSheetSurface {
            InviteSheetContent(
                InviteSheetState(code = "AB12CD", step = InviteStep.FOUND, preview = invitePreview),
                CrewFindActions()
            )
        }
    }

    @Test
    fun search() = compose.captureRunkkScreen("F01d_crew_search") {
        CrewSearchScreen(
            CrewSearchUiState(keyword = "새벽", hasSearched = true, results = listOf(crewA, crewC), resultCount = 2),
            CrewSearchActions()
        )
    }

    @Test
    fun searchEmpty() = compose.captureRunkkScreen("F01e_crew_search_empty") {
        CrewSearchScreen(CrewSearchUiState(keyword = "없는크루", hasSearched = true), CrewSearchActions())
    }

    @Test
    fun create() = compose.captureRunkkScreen("G2_crew_create") {
        CrewCreateScreen(
            CrewCreateUiState(
                CrewFormState(
                    name = "새벽질주",
                    intro = "새벽에 같이 달려요",
                    tags = setOf("WEEKDAY_EVENING", "WEEKDAY_DAWN", "WEEKEND_MORNING")
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

    /**
     * 크루런 탭(피그마 F-11): 29분 45초 뒤 크루런이 강조 카드, 이번주 둘(하나는 내가 참석)·다음주 하나, 지난 크루런 하나.
     */
    private val crewRunsHome: CrewHomeUiState = run {
        val now = start.plusSeconds(15)
        val base = crewRunPosts.posts[0]
        fun upcoming(
            id: Long,
            title: String,
            at: java.time.Instant,
            km: Int,
            going: Int,
            mine: AttendanceAnswer? = null
        ) = base.copy(
            postId = id,
            summary = title,
            crewRun = crewRunVoting.copy(
                scheduledAt = at,
                distance = Distance(km * 1_000L),
                goingCount = going,
                myAnswer = mine
            )
        )
        val hero = upcoming(31, "오늘 저녁 같이 5km", start.plusSeconds(1_800), 5, 5, AttendanceAnswer.GOING)
        val runs = listOf(
            hero,
            upcoming(33, "토요일 한강 같이 뛰어요", start.plusSeconds(302_400), 5, 5, AttendanceAnswer.GOING),
            upcoming(34, "주말 롱런", start.plusSeconds(388_800), 10, 4),
            upcoming(35, "토요일 한강 같이 뛰어요", start.plusSeconds(907_200), 5, 5),
            crewRunPosts.posts[1]
        )
        home.copy(
            tab = CrewTab.SCHEDULE,
            crewRuns = PostList(runs),
            scheduleNow = now,
            lobbyEntries = runs.mapNotNull { p -> p.crewRun?.let { p.postId to LobbyAvailability.of(it, now) } }.toMap()
        )
    }

    @Test
    fun crewRuns() = compose.captureRunkkScreen("G3_my_crew_crew_runs") {
        CrewHomeScreen(crewRunsHome, CrewHomeActions())
    }

    @Test
    fun crewRunsLight() = compose.captureRunkkScreen("G3_my_crew_crew_runs_light", isDarkTheme = false) {
        CrewHomeScreen(crewRunsHome, CrewHomeActions())
    }

    @Test
    fun crewRunDetail() = compose.captureRunkkScreen("G3_crew_run_detail") {
        CrewRunDetailScreen(
            CrewRunDetailUiState(postId = 42, detail = crewRunDetail, now = crewRunDetail.writtenAt.plusSeconds(7_200)),
            CrewRunDetailActions()
        )
    }

    @Test
    fun crewRunDetailMine() = compose.captureRunkkScreen("G3_crew_run_detail_mine", isDarkTheme = false) {
        CrewRunDetailScreen(
            CrewRunDetailUiState(
                postId = 42,
                detail = crewRunDetail.copy(
                    isMine = true,
                    canEdit = true,
                    canDelete = true,
                    myAnswer = AttendanceAnswer.GOING,
                    attendees = crewRunDetail.attendees.map { if (it.isAuthor) it.copy(isMe = true) else it }
                ),
                now = crewRunDetail.writtenAt.plusSeconds(7_200),
                isMenuOpen = true
            ),
            CrewRunDetailActions()
        )
    }

    @Test
    fun crewRunDeleteConfirm() = compose.captureRunkkScreen("G3_crew_run_delete_confirm", isDarkTheme = false) {
        CrewRunDetailScreen(
            CrewRunDetailUiState(
                postId = 42,
                detail = crewRunDetail.copy(isMine = true, canEdit = true, canDelete = true),
                now = crewRunDetail.writtenAt.plusSeconds(7_200),
                isDeleteAsked = true
            ),
            CrewRunDetailActions()
        )
    }

    @Test
    fun `크루원 쪽은 내 정보 카드와 크루원별 누적 거리를 보인다(피그마 F-22, W7)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(home.copy(page = CrewHomePage.MEMBERS, members = membersWithMe), CrewHomeActions())
            }
        }
        compose.onNodeWithText("내 정보").assertExists()
        compose.onNodeWithText("누적 거리").assertExists()
        compose.onNodeWithText("512.39km").assertExists()
        compose.onNodeWithText("MORNING_SPARROW").assertDoesNotExist()
    }

    @Test
    fun memberList() = compose.captureRunkkScreen("G3_my_crew_members") {
        CrewHomeScreen(home.copy(page = CrewHomePage.MEMBERS), CrewHomeActions())
    }

    @Test
    fun write() = compose.captureRunkkScreen("G5_crew_run_write", isDarkTheme = false) {
        CrewRunWriteScreen(
            CrewRunWriteUiState(
                mode = CrewRunWriteMode.Create(11),
                title = "토요일 한강 같이 뛰어요",
                today = LocalDate.of(2026, 10, 20)
            ),
            CrewRunWriteActions()
        )
    }

    @Test
    fun writeEdit() = compose.captureRunkkScreen("G5_crew_run_edit", isDarkTheme = false) {
        CrewRunWriteScreen(
            CrewRunWriteUiState(
                mode = CrewRunWriteMode.Edit(42),
                title = "토요일 한강 같이 뛰어요",
                body = "반포 8번 출구, 6분대 페이스",
                date = LocalDate.of(2026, 10, 24),
                time = java.time.LocalTime.of(7, 0),
                distance = CrewRunDistanceChoice.KM5,
                repeat = com.a207.runkk.app.domain.crew.CrewRunRepeat.WEEKLY,
                today = LocalDate.of(2026, 10, 20)
            ),
            CrewRunWriteActions()
        )
    }

    @Test
    fun writeDateSheet() = compose.captureRunkkScreen("G5_crew_run_write_date", isDarkTheme = false) {
        CrewRunWriteScreen(
            CrewRunWriteUiState(
                mode = CrewRunWriteMode.Create(11),
                today = LocalDate.of(2026, 10, 21),
                sheet = CrewRunWriteSheet.DATE
            ),
            CrewRunWriteActions()
        )
    }

    @Test
    fun writeRepeatSheet() = compose.captureRunkkScreen("G5_crew_run_write_repeat", isDarkTheme = false) {
        CrewRunWriteScreen(
            CrewRunWriteUiState(
                mode = CrewRunWriteMode.Create(11),
                date = LocalDate.of(2026, 10, 24),
                today = LocalDate.of(2026, 10, 21),
                repeat = com.a207.runkk.app.domain.crew.CrewRunRepeat.WEEKLY,
                sheet = CrewRunWriteSheet.REPEAT
            ),
            CrewRunWriteActions()
        )
    }

    /** 대기실(F-14b): 내 준비(isReady)와 격자의 내 칸 상태를 맞춘다(대기실 응답의 내 줄이 준비 완료). */
    private val readyLobby = CrewRunUiState(
        postId = 31,
        distance = Distance(5_000),
        phase = CrewRunPhase.LOBBY,
        lobby = lobbyWithAbsent.copy(
            participants = lobbyWithAbsent.participants.map { if (it.isMe) it.copy(isReady = true) else it }
        ),
        isReady = true,
        untilStart = Duration.ofSeconds(512)
    )

    /** 취소(F-14c): 마지막 대기실 그대로, 남은 시간 0, 서버 취소 사유. */
    private val cancelledLobby = CrewRunUiState(
        postId = 31,
        distance = Distance(5_000),
        phase = CrewRunPhase.CANCELLED,
        lobby = lobbyWithAbsent,
        untilStart = Duration.ZERO,
        cancelledReason = "준비 완료한 참가자가 2명보다 적어 시작하지 않았어요"
    )

    @Test
    fun lobbyScreen() = compose.captureRunkkScreen("G6_crew_run_lobby") { CrewRunScreen(readyLobby, CrewRunActions()) }

    @Test
    fun lobbyLight() = compose.captureRunkkScreen("G6_crew_run_lobby_light", isDarkTheme = false) {
        CrewRunScreen(readyLobby, CrewRunActions())
    }

    @Test
    fun cancelled() = compose.captureRunkkScreen("G6_crew_run_cancelled") {
        CrewRunScreen(cancelledLobby, CrewRunActions())
    }

    @Test
    fun cancelledLight() = compose.captureRunkkScreen("G6_crew_run_cancelled_light", isDarkTheme = false) {
        CrewRunScreen(cancelledLobby, CrewRunActions())
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
    fun relayLight() = compose.captureRunkkScreen("G7_crew_run_relay_light", isDarkTheme = false) {
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
    fun quitConfirmLight() = compose.captureRunkkScreen("G7_crew_run_quit_confirm_light", isDarkTheme = false) {
        CrewRunScreen(runningState().copy(isConfirmingQuit = true), CrewRunActions())
    }

    @Test
    fun result() = compose.captureRunkkScreen("G8_crew_run_result") {
        CrewRunResultScreen(CrewRunResultUiState(isLoading = false, result = crewRunResult), {}, {}, {})
    }

    @Test
    fun resultLight() = compose.captureRunkkScreen("G8_crew_run_result_light", isDarkTheme = false) {
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

/** 초대 코드 시트 안쪽을 시트처럼 표면 바탕(위 모서리 28, 안쪽 24/20/32)에 그린다(스냅샷은 시트 창 없이 찍는다). */
@Composable
private fun InviteSheetSurface(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(RunkkColors.Scrim)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RunkkShapes.Sheet)
                .background(RunkkColors.Surface)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp)
        ) { content() }
    }
}
