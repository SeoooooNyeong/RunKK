package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.BoardPost
import com.a207.runkk.app.domain.crew.PostList
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 게시판 탭 내용(와이어프레임 E3·M6): 종류 칩, 단추 줄, 글 종류별 모양, ⋯ 메뉴, 응원 칸, 앨범과 라이트·다크 스크린샷. */
@RunWith(RobolectricTestRunner::class)
class CrewBoardFeedTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    /** 사진 5장 게시글(셋째 칸에 +2). */
    private val manyPhotos = boards.posts[0].copy(
        postId = 85,
        board = BoardPost("지난 주말 롱런 사진 올려요", (1..5L).map { BoardPost.Photo(910 + it, "/p/$it") }),
        summary = "지난 주말 롱런 사진 올려요",
        writtenAt = start.minusSeconds(90_000)
    )

    private fun feed(isManager: Boolean = false, filter: BoardFilter = BoardFilter.ALL) = CrewBoardUiState(
        crewId = 11,
        filter = filter,
        posts = PostList(notices().posts.drop(1) + polls.posts.take(1) + manyPhotos + cheers.posts.take(2)),
        pinned = notices().posts.take(1),
        album = album,
        headcount = 6,
        canManage = isManager,
        now = start
    )

    private val actions = CrewBoardActions(
        onFilter = { calls += "filter:$it" },
        onReport = { calls += "report:${it.postId}" },
        onPinToggle = { calls += "pin:${it.postId}" },
        onNavigate = { calls += "nav:$it" },
        loadPhoto = { Outcome.Failure(AppError.Network) }
    )

    @Composable
    private fun Board(state: CrewBoardUiState) {
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { boardTabItems(state, actions, onWrite = { calls += "write" }) }
    }

    private fun show(state: CrewBoardUiState) = compose.setContent { RunkkTheme(reduceMotion = true) { Board(state) } }

    @Test
    fun `칩을 누르면 종류를 바꾸고 크루원에게는 공지 단추가 없다`() {
        show(feed())
        compose.onNodeWithContentDescription("공지 쓰기").assertDoesNotExist()
        compose.onNodeWithText("앨범").performClick()
        compose.onNodeWithText("글쓰기").performClick()
        compose.onNodeWithText("투표 만들기").performClick()
        assertEquals(listOf("filter:ALBUM", "write", "nav:${Routes.CrewPollWrite(11)}"), calls)
    }

    @Test
    fun `운영진은 공지 단추로 공지 쓰기를 열고 공지 ⋯ 메뉴에서 고정을 바꾼다(FR-205)`() {
        show(feed(isManager = true))
        compose.onNodeWithContentDescription("공지 쓰기").performClick()
        compose.onNodeWithContentDescription("바람처럼 글 메뉴: 토요일 정기런은 7시 반포").performClick()
        compose.onNodeWithText("고정 해제").performClick()
        assertEquals(listOf("nav:${Routes.CrewNoticeWrite(11)}", "pin:51"), calls)
    }

    @Test
    fun `투표 글은 응답 수와 인원, 마감을 보이고 투표하기로 투표 화면을 연다(FR-206)`() {
        show(feed())
        compose.onNodeWithText("응답 3/6명, 10.21 (수) 19:00 마감").assertExists()
        compose.onNodeWithText("투표하기").performClick()
        compose.onNodeWithContentDescription("달빛 글 메뉴: 지난 주말 롱런 사진 올려요").performClick()
        compose.onNodeWithText("신고").performClick()
        assertEquals(listOf("nav:${Routes.CrewPoll(11, 61)}", "report:85"), calls)
    }

    @Test
    fun `사진이 셋보다 많으면 셋째 칸에 남은 장 수를 얹는다`() {
        show(feed())
        compose.onNodeWithContentDescription("달빛님의 사진 3, 크게 보기, 2장 더").assertExists()
        compose.onNodeWithText("어제").assertExists()
        compose.onNodeWithText("바람처럼, 10분 전").assertExists()
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun feedDark() = compose.captureRunkkScreen("E3_crew_board_feed_dark", isDarkTheme = true) { Board(feed(true)) }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun feedLight() = compose.captureRunkkScreen("E3_crew_board_feed_light", isDarkTheme = false) { Board(feed(true)) }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun cheerDark() = compose.captureRunkkScreen("E3_crew_board_cheer_dark", isDarkTheme = true) {
        Board(feed().copy(filter = BoardFilter.CHEER, posts = cheers, pinned = emptyList(), cheerDraft = "이번 주도 화이팅"))
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun albumDark() = compose.captureRunkkScreen("M6_crew_album_dark", isDarkTheme = true) {
        Board(feed().copy(filter = BoardFilter.ALBUM))
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun albumLight() = compose.captureRunkkScreen("M6_crew_album_light", isDarkTheme = false) {
        Board(feed().copy(filter = BoardFilter.ALBUM))
    }
}
