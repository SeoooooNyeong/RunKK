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
        ranking = ranking
    )

    @Test
    fun `크루원의 ⋯ 메뉴는 크루 정보와 크루원이고 운영진에게는 크루 관리와 목표 설정이 있다(FR-128, F-17·F-24)`() {
        var role by mutableStateOf(CrewRole.MEMBER)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(role).copy(race = raceWithGoal.copy(celebration = null)),
                    CrewHomeActions(onPage = { calls += "page:$it" }, onManage = { calls += "manage" })
                )
            }
        }
        compose.onNodeWithText("목표 설정").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루원").assertExists()
        compose.onNodeWithText("크루 관리").assertDoesNotExist()
        compose.onNodeWithText("크루 정보").performClick()
        role = CrewRole.MANAGER
        compose.waitForIdle()
        compose.onNodeWithText("목표 설정").assertExists()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루 관리").performClick()
        assertEquals(listOf("page:INFO", "manage"), calls)
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
        crewRuns = crewRunPosts
    )

    private fun crewHome(isDark: Boolean) = compose.captureRunkkScreen("A3b_crew_home_race_${theme(isDark)}", isDark) {
        CrewHomeScreen(home, CrewHomeActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun homeDark() = crewHome(isDark = true)

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun homeLight() = crewHome(isDark = false)

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
