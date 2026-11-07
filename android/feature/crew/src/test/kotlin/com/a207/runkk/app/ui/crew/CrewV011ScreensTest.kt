package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PostList
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.11 크루 화면: 권한표 버튼 노출(3.0.3), 고정 공지, 공동 목표·기여도·축하 카드, 대면 모임, 투표, 게시판, 운영진 지정. */
@RunWith(RobolectricTestRunner::class)
class CrewV011ScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun home(role: CrewRole, tab: CrewTab = CrewTab.RACE) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = role),
        tab = tab,
        race = raceWithGoal,
        ranking = ranking,
        meetups = meetups,
        scheduleKind = PostList.Kind.MEETUP
    )

    @Test
    fun `크루원에게는 크루 메뉴·목표 설정이 없고 운영진에게는 있다(FR-128, 3·0·3)`() {
        var role by mutableStateOf(CrewRole.MEMBER)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(home(role).copy(race = raceWithGoal.copy(celebration = null)), CrewHomeActions())
            }
        }
        compose.onNodeWithText("목표 설정").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").assertDoesNotExist()
        role = CrewRole.MANAGER
        compose.waitForIdle()
        compose.onNodeWithText("목표 설정").assertExists()
        compose.onNodeWithContentDescription("크루 메뉴").assertExists()
    }

    @Test
    fun `레이스 - 공동 목표 카드는 합계, 목표, 진행률을 읽고 목표 설정으로 간다(와이어 E2)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER).copy(race = raceWithGoal.copy(celebration = null)),
                    CrewHomeActions(onGoalEdit = { calls += "goal" })
                )
            }
        }
        compose.onNodeWithContentDescription("공동 목표 50.0킬로미터 중 58.6킬로미터, 100퍼센트").assertExists()
        compose.onNodeWithText("목표 설정").performClick()
        assertEquals(listOf("goal"), calls)
    }

    @Test
    fun `레이스 - 목표가 없는 주는 이번 주 달린 거리로 읽히고 마감은 없다(F-06)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER).copy(race = race.copy(contributors = raceWithGoal.contributors)),
                    CrewHomeActions()
                )
            }
        }
        compose.onNodeWithText("이번 주 달린 거리").assertExists()
        compose.onNodeWithText("이번 주 공동 목표").assertDoesNotExist()
        compose.onNodeWithText("일요일 자정 마감").assertDoesNotExist()
        compose.onNodeWithText("목표 설정").assertExists()
    }

    @Test
    fun `레이스 - 달성한 주는 축하 카드와 공유, 기여도 막대는 순위 숫자 없이 읽는다(와이어 E9, FR-140·FR-141)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER),
                    CrewHomeActions(
                        onShareCelebration = { calls += "share:${it.crewName}" },
                        onGoalEdit = { calls += "goal" },
                        onChat = { calls += "chat" }
                    )
                )
            }
        }
        compose.onNodeWithText("함께 목표를 넘었어요", substring = true).assertExists()
        compose.onNodeWithText("이번 주").assertExists()
        compose.onNodeWithContentDescription("달빛 27.5킬로미터").assertExists()
        compose.onNodeWithContentDescription("나 12.9킬로미터").assertExists()
        compose.onNodeWithText("목표 설정").assertDoesNotExist()
        compose.onNodeWithText("축하 카드 공유").performClick()
        compose.onNodeWithText("채팅").performClick()
        assertEquals(listOf("share:새벽질주", "chat"), calls)
    }

    @Test
    fun `고정 공지는 레이스 탭 위에 보이고 줄은 게시판 공지 칩으로, + 공지는 공지 쓰기로(FR-205, 와이어 E2)`() {
        var role by mutableStateOf(CrewRole.MEMBER)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(role).copy(pinnedNotices = notices().posts.take(2)),
                    CrewHomeActions(onNotices = { calls += "notices" }, onNoticeWrite = { calls += "write" })
                )
            }
        }
        compose.onNodeWithText("+ 공지").assertDoesNotExist()
        compose.onNodeWithContentDescription("고정 공지, 회비 안내").performClick()
        role = CrewRole.MANAGER
        compose.waitForIdle()
        compose.onNodeWithText("+ 공지").performClick()
        assertEquals(listOf("notices", "write"), calls)
    }

    @Test
    fun `일정 - 대면 모임은 장소·정원·응답 수와 참석·불참·미정, 남의 글은 신고(FR-145, FR-214)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.MEMBER, CrewTab.SCHEDULE),
                    CrewHomeActions(
                        onAttendance = { post, answer -> calls += "${post.postId}:$answer" },
                        onReport = { calls += "report:${it.postId}" }
                    )
                )
            }
        }
        compose.onNode(hasText("여의나루역 2번 출구", substring = true)).assertExists()
        compose.onNodeWithText("참석 2, 불참 1, 미정 0").assertExists()
        compose.onNodeWithText("정원 2/8명", substring = true).assertExists()
        compose.onNodeWithText("미정").performClick()
        compose.onNodeWithContentDescription("바람처럼 글 신고: 번개 모임 후 맥주").performClick()
        compose.onNodeWithText("삭제").assertDoesNotExist()
        assertEquals(listOf("71:${AttendanceAnswer.UNDECIDED}", "report:71"), calls)
    }

    @Test
    fun `게시판 - 사진을 누르면 크게 보고 ⋯ 메뉴에는 내 글만 삭제가 있다(FR-211~FR-213)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                LazyColumn {
                    boardTabItems(
                        CrewBoardUiState(crewId = 11, posts = boards),
                        CrewBoardActions(
                            onOpenPhoto = { calls += "photo:$it" },
                            onDeleteAsk = { calls += "delete:${it.postId}" },
                            loadPhoto = { Outcome.Failure(AppError.Network) }
                        ),
                        onWrite = {}
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("달빛님의 사진 2, 크게 보기").performClick()
        compose.onNodeWithContentDescription("나 글 메뉴: 신발 추천 받아요").performClick()
        compose.onNodeWithText("신고").assertDoesNotExist()
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithContentDescription("달빛 글 메뉴: 오늘 한강 노을 미쳤다").performClick()
        compose.onNodeWithText("삭제").assertDoesNotExist()
        assertEquals(listOf("photo:902", "delete:82"), calls)
    }

    @Test
    fun `크루 관리 - 크루장은 크루원 메뉴에서 운영진 지정·해제, 운영진은 가입 결정만 본다(FR-204, 와이어 I10)`() {
        var role by mutableStateOf(CrewRole.LEADER)
        var menu by mutableStateOf<com.a207.runkk.app.domain.crew.CrewMembers.Member?>(null)
        val withManager = members.copy(
            members = members.members.map {
                if (it.memberId ==
                    3L
                ) {
                    it.copy(role = CrewRole.MANAGER)
                } else {
                    it
                }
            }
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewManageScreen(
                    CrewManageUiState(
                        crewId = 11,
                        detail = crewDetail(role = role),
                        isLoading = false,
                        members = withManager,
                        menuMember = menu
                    ),
                    CrewManageActions(
                        onMenu = { calls += "menu:${it.memberId}" },
                        onAsk = { calls += "ask:${(it as ManageAction.Appoint).member.memberId}:${it.managerCount}" },
                        onDismissManager = { calls += "dismiss:${it.memberId}" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription("달빛 크루원 메뉴").performClick()
        menu = withManager.members[1]
        compose.waitForIdle()
        compose.onNodeWithText("운영진으로 지정").performClick()
        menu = withManager.members[2]
        compose.waitForIdle()
        compose.onNodeWithText("운영진 해제").performClick()
        assertEquals(listOf("menu:2", "ask:2:1", "dismiss:3"), calls)
        menu = null
        role = CrewRole.MANAGER
        compose.waitForIdle()
        compose.onNodeWithContentDescription("달빛 크루원 메뉴").assertDoesNotExist()
        compose.onNodeWithText("크루 삭제").assertDoesNotExist()
    }

    @Test
    fun `게시판 글쓰기 - 5장이면 사진 고르기를 막는다(TC-7439)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewBoardWriteScreen(
                    CrewBoardWriteUiState(body = "노을", photos = (1..5).map { "content://p/$it" }),
                    onBack = {},
                    onBody = {},
                    onPickPhotos = {},
                    onRemovePhoto = {},
                    onSubmit = {}
                )
            }
        }
        compose.onNodeWithText("5/5장").assertExists()
        compose.onNodeWithContentDescription("사진 추가").assertIsNotEnabled()
    }
}

/** v0.11 크루 화면 스크린샷: 크루 홈(레이스)·일정(대면 모임)·공지·투표·게시판 × 라이트·다크(NFR-041), 쓰기 화면. */
@RunWith(RobolectricTestRunner::class)
class CrewV011ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = CrewRole.MANAGER),
        race = raceWithGoal,
        ranking = ranking,
        meetups = meetups,
        crewRuns = crewRunPosts
    )
    private val pinned = notices().posts.take(2)

    private fun crewHome(isDark: Boolean) = compose.captureRunkkScreen("A3b_crew_home_race_${theme(isDark)}", isDark) {
        CrewHomeScreen(home.copy(pinnedNotices = pinned), CrewHomeActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun homeDark() = crewHome(isDark = true)

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun homeLight() = crewHome(isDark = false)

    private fun board(isDark: Boolean) = compose.captureRunkkScreen("A3b_crew_board_${theme(isDark)}", isDark) {
        CrewHomeScreen(
            home.copy(tab = CrewTab.BOARD, pinnedNotices = pinned),
            CrewHomeActions(),
            lazyTab = {
                boardTabItems(
                    CrewBoardUiState(crewId = 11, posts = boards),
                    CrewBoardActions(loadPhoto = { Outcome.Failure(AppError.Network) }),
                    onWrite = {}
                )
            }
        )
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun boardDark() = board(isDark = true)

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun boardLight() = board(isDark = false)

    private fun meetup(isDark: Boolean) =
        compose.captureRunkkScreen("A3b_crew_schedule_meetup_${theme(isDark)}", isDark) {
            CrewHomeScreen(home.copy(tab = CrewTab.SCHEDULE, scheduleKind = PostList.Kind.MEETUP), CrewHomeActions())
        }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupDark() = meetup(isDark = true)

    /** 라이트 벌 기준 이미지(FR-203, NFR-041, AN5-D-12). */
    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupLight() = meetup(isDark = false)

    private fun pollWrite(isDark: Boolean) =
        compose.captureRunkkScreen("A3b_crew_poll_write_${theme(isDark)}", isDark) {
            CrewPollWriteScreen(
                CrewPollWriteUiState(
                    question = "다음 달 대회 어디 나갈까요?",
                    method = PollMethod.CHOICES,
                    options = listOf("춘천", "JTBC", ""),
                    today = LocalDate.of(2026, 10, 20)
                ),
                {},
                {},
                {},
                { _, _ -> },
                {},
                {},
                {},
                {},
                {}
            )
        }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun pollWriteDark() = pollWrite(isDark = true)

    /** 라이트 벌 기준 이미지(FR-203, NFR-041, AN5-D-12). */
    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun pollWriteLight() = pollWrite(isDark = false)

    private fun boardWrite(isDark: Boolean) =
        compose.captureRunkkScreen("A3b_crew_board_write_${theme(isDark)}", isDark) {
            CrewBoardWriteScreen(
                CrewBoardWriteUiState(body = "오늘 한강 노을 미쳤다", photos = listOf("content://p/1", "content://p/2")),
                onBack = {},
                onBody = {},
                onPickPhotos = {},
                onRemovePhoto = {},
                onSubmit = {}
            )
        }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun boardWriteDark() = boardWrite(isDark = true)

    /** 라이트 벌 기준 이미지(FR-203, NFR-041, AN5-D-12). */
    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun boardWriteLight() = boardWrite(isDark = false)

    private fun meetupWrite(isDark: Boolean) =
        compose.captureRunkkScreen("A3b_crew_meetup_write_${theme(isDark)}", isDark) {
            CrewRunWriteScreen(
                CrewRunWriteUiState(
                    kind = PostList.Kind.MEETUP,
                    body = "번개 후 맥주",
                    placeName = "여의나루역 2번 출구",
                    capacity = 8,
                    today = LocalDate.of(2026, 10, 20),
                    dayOffset = 2
                ),
                {},
                {},
                {},
                {},
                {},
                {},
                {}
            )
        }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupWriteDark() = meetupWrite(isDark = true)

    /** 라이트 벌 기준 이미지(FR-203, NFR-041, AN5-D-12). */
    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupWriteLight() = meetupWrite(isDark = false)
}

/** 스크린샷 파일 이름 끝(라이트·다크 벌, NFR-041). */
private fun theme(isDark: Boolean): String = if (isDark) "dark" else "light"
