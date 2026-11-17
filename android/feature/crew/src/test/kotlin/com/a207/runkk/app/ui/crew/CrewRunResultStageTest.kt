package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RESULT_STAGE_BACKDROP_TAG
import com.a207.runkk.app.core.designsystem.RESULT_STAGE_DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 크루런 결과의 무대([임시:FR-성-33], [임시:D-성-286], [임시:D-성-307]): 결과 쪽 위에 무대 배경과 내 전신 아바타(발끝 266, 높이 180)가
 * 서고, 크루원 얼굴 줄은 없다(참가자 카드의 얼굴만 남는다). 상세 쪽은 그대로다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewRunResultStageTest {
    @get:Rule
    val compose = createComposeRule()

    private var seen: AvatarStillSubject? = null

    private val still: @Composable () -> Unit = {
        seen = LocalSlotAvatarStill.current
        Box(Modifier.fillMaxSize().testTag("still"))
    }

    private fun show(page: Int = 0, slot: (@Composable () -> Unit)? = still) = compose.setContent {
        CompositionLocalProvider(
            // 꾸미기가 닫힌 범위에서는 크루원 아바타가 기본 그림(시험 표지 있음)이라 얼굴 줄이 없음을 셀 수 있다.
            LocalReleaseScope provides ReleaseScope.of(isLockingUnreleased = true),
            LocalSlots provides (slot?.let { mapOf(SlotKey.AVATAR_STILL to listOf(it)) } ?: emptyMap())
        ) {
            RunkkTheme(reduceMotion = true) {
                CrewRunResultScreen(
                    CrewRunResultUiState(isLoading = false, result = crewRunResult),
                    onCard = {},
                    onHome = {},
                    onRetry = {},
                    pagerState = PagerState(currentPage = page) { 2 }
                )
            }
        }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected $expected, actual $actual")

    @Test
    fun `결과 쪽에 무대와 내 전신 아바타가 있고 크루원 얼굴 행이 없다`() {
        show()
        compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).assertExists()
        assertEquals(AvatarStillSubject.MINE, seen)
        val avatar = compose.onNodeWithTag("still").getUnclippedBoundsInRoot()
        assertNear(180.dp, avatar.bottom - avatar.top, "아바타 높이")
        assertNear(266.dp, avatar.bottom, "발끝")
        // 예전 아바타 줄(가운데 내 아바타와 양옆 크루원 넷)은 기쁨 자세 그림이었다.
        assertEquals(0, compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + "JOY").fetchSemanticsNodes().size)
        // 참가자 카드(이름과 얼굴)는 남는다.
        compose.onNodeWithText("바람처럼", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("크루런 결과").assertExists()
    }

    @Test
    fun `결과 쪽 시트 위가 300dp다`() {
        show()
        assertNear(300.dp, compose.onNodeWithTag(CREW_RESULT_SHEET_TAG).getUnclippedBoundsInRoot().top, "시트 위")
    }

    @Test
    fun `전신 정지 그림 칸이 비면 기본 서기 그림이 같은 자리에 선다`() {
        show(slot = null)
        val avatar = compose.onNodeWithTag(RESULT_STAGE_DEFAULT_AVATAR_TAG).getUnclippedBoundsInRoot()
        assertNear(180.dp, avatar.bottom - avatar.top, "아바타 높이")
        assertNear(266.dp, avatar.bottom, "발끝")
    }

    @Test
    fun `상세 쪽은 그대로다(기록 전이면 기다림 문구, 시트 없음)`() {
        show(page = 1)
        compose.onNodeWithText("상세 기록을 불러오고 있어요").assertExists()
        compose.onNodeWithText("‹ 크루런 결과로").assertExists()
    }

    @Test
    fun `페이저를 넘기면 무대 알파가 줄어든다`() {
        assertEquals(1f, summaryPageVisibility(currentPage = 0, offsetFraction = 0f))
        assertEquals(0.75f, summaryPageVisibility(currentPage = 0, offsetFraction = 0.25f))
        assertEquals(0f, summaryPageVisibility(currentPage = 1, offsetFraction = 0f))
        assertEquals(0f, summaryPageVisibility(currentPage = 1, offsetFraction = 0.3f))
        assertEquals(0.3f, summaryPageVisibility(currentPage = 1, offsetFraction = -0.3f), 0.001f)
    }
}
