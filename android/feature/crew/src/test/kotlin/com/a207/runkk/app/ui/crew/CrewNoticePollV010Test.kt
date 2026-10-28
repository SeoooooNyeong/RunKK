package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.crew.CrewRole
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 공지 쓰기(와이어프레임 M1)와 투표 화면(M3): 화면 동작과 라이트·다크 스크린샷. */
@RunWith(RobolectricTestRunner::class)
class CrewNoticePollScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private val noticeState = CrewNoticeWriteUiState(body = "토요일 정기런은 7시 반포에서 모여요", pinnedCount = 2)

    private fun pollState(myOptionNo: Int? = 1) = CrewPollDetailUiState(
        crewId = 11,
        postId = 61,
        post = polls.posts[0],
        result = pollResult(61, myOptionNo),
        now = start,
        isLoading = false
    )

    @Test
    fun `공지 쓰기 - 고정 스위치와 지금 고정된 공지 수, 올리기`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewNoticeWriteScreen(
                    noticeState,
                    NoticeWriteActions(onPinned = { calls += "pin:$it" }, onSubmit = { calls += "submit" })
                )
            }
        }
        compose.onNodeWithText("지금 고정된 공지 2/3").assertExists()
        compose.onNodeWithText("상단에 고정").performClick()
        compose.onNodeWithText("올리기").performClick()
        assertEquals(listOf("pin:false", "submit"), calls)
    }

    @Test
    fun `투표 - 선택지를 누르면 응답하고 응답자가 없으면 아직 없어요, 마감 임박을 보인다(FR-207)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewPollScreen(
                    pollState(),
                    PollDetailActions(onRespond = { calls += "respond:$it" }, onReport = {
                        calls +=
                            "report"
                    })
                )
            }
        }
        compose.onNodeWithText("마감 임박").assertExists()
        compose.onNodeWithText("응답자 3명").assertExists()
        compose.onNodeWithText("아직 없어요").assertExists()
        compose.onNodeWithText("내 선택: 춘천").assertExists()
        compose.onNodeWithContentDescription("JTBC, 1명. 비공개러너").performClick()
        compose.onNodeWithContentDescription("투표 메뉴").performClick()
        compose.onNodeWithText("삭제").assertDoesNotExist()
        compose.onNodeWithText("신고").performClick()
        assertEquals(listOf("respond:2", "report"), calls)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun noticeWriteDark() = compose.captureRunkkScreen("M1_crew_notice_write_dark", isDarkTheme = true) {
        CrewNoticeWriteScreen(noticeState, NoticeWriteActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun noticeWriteLight() = compose.captureRunkkScreen("M1_crew_notice_write_light", isDarkTheme = false) {
        CrewNoticeWriteScreen(noticeState, NoticeWriteActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun pollDark() = compose.captureRunkkScreen("M3_crew_poll_dark", isDarkTheme = true) {
        CrewPollScreen(pollState(), PollDetailActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun pollLight() = compose.captureRunkkScreen("M3_crew_poll_light", isDarkTheme = false) {
        CrewPollScreen(pollState(), PollDetailActions())
    }
}

/** 공지 쓰기(M1)와 투표 화면(M3) ViewModel. */
class CrewNoticePollViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `공지 쓰기 - 고정 공지 수를 세고 올리면서 고정한다(FR-205)`() {
        val vm = CrewNoticeWriteViewModel(repo).also { it.bind(11) }
        assertEquals(2, vm.state.value.pinnedCount)
        assertTrue(vm.state.value.isPinned)
        vm.onBody("  ")
        assertFalse(vm.state.value.canSubmit)
        vm.onBody("우천 시 취소")
        vm.onSubmit(11)
        assertTrue("notice:우천 시 취소:true" in repo.calls)
        assertTrue(vm.state.value.isPosted)
    }

    @Test
    fun `투표 화면 - 결과와 작성자를 읽고 응답 뒤 결과를 다시 읽는다, 내 글만 지운다`() {
        val vm = CrewPollDetailViewModel(repo, MutableClock()).also { it.bind(11, 61) }
        assertEquals("달빛", vm.state.value.post?.authorName)
        vm.onRespond(2)
        assertEquals(2, repo.calls.count { it == "pollResult:61" })
        assertEquals(2, vm.state.value.result?.myOptionNo)
        vm.onDeleteAsk()
        assertFalse(vm.state.value.isConfirmingDelete)

        repo.role = CrewRole.MEMBER
        val mine = CrewPollDetailViewModel(repo, MutableClock()).also { it.bind(11, 62) }
        mine.onDeleteAsk()
        mine.onDeleteConfirm()
        assertTrue("deletePost:62" in repo.calls)
        assertTrue(mine.state.value.isDeleted)
    }
}
