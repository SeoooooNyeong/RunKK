package com.a207.runkk.app.ui.run

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillFallback
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.account.MyAvatar
import java.time.Duration
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 자유런 결과 화면의 무대([임시:FR-성-33], [임시:D-성-284]): 위쪽 얼굴 원 대신 무대 배경 위에 선 내 전신 아바타(발끝 266, 높이 180)를
 * 두고, 결과 내용은 300부터 올라오는 시트에 담는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunResultScreenStageTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = RunResultUiState(
        isLoading = false,
        recordId = "r",
        cardKind = CardKind.FREE_RUN,
        distance = Distance(5_210),
        elapsed = Duration.ofSeconds(1_668),
        myAvatar = MyAvatar("default:CAT", emptyList(), "PARIS")
    )

    private var seen: AvatarStillSubject? = null

    /** 전신 정지 그림 칸을 채운 기능 흉내: 넘겨받은 사람을 적고 칸을 채운다. */
    private val still: @Composable () -> Unit = {
        seen = LocalSlotAvatarStill.current
        Box(Modifier.fillMaxSize().testTag("still"))
    }

    private fun show(
        scope: ReleaseScope = ReleaseScope.ALL_OPEN,
        slot: (@Composable () -> Unit)? = still,
        state: RunResultUiState = this.state
    ) = compose.setContent {
        CompositionLocalProvider(
            LocalReleaseScope provides scope,
            LocalSlots provides (slot?.let { mapOf(SlotKey.AVATAR_STILL to listOf(it)) } ?: emptyMap())
        ) {
            RunkkTheme(reduceMotion = true) { RunResultScreen(state, onCard = {}, onHome = {}, onRetry = {}) }
        }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected $expected, actual $actual")

    @Test
    fun `얼굴 원이 사라지고 무대와 내 전신 아바타 칸이 있다`() {
        show()
        compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).assertExists()
        assertEquals(AvatarStillSubject.MINE, seen)
        val avatar = compose.onNodeWithTag("still").getUnclippedBoundsInRoot()
        assertNear(180.dp, avatar.bottom - avatar.top, "아바타 높이")
        assertNear(266.dp, avatar.bottom, "발끝")
        assertNear(144.dp, avatar.right - avatar.left, "아바타 너비")
        // 히어로 오른쪽의 뛰어오르는 아바타(얼굴 원)는 없다.
        assertEquals(0, compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + "JUMP").fetchSemanticsNodes().size)
        compose.onNodeWithText("러닝 결과").assertExists()
    }

    @Test
    fun `꾸미기가 닫히면 기본 서기 그림이다`() {
        // 칸을 채운 기능은 꾸미기가 닫혀 있으면 부르는 쪽 그림을 그린다.
        show(ReleaseScope.FIRST_RELEASE, slot = { LocalSlotAvatarStillFallback.current?.invoke() })
        val avatar = compose.onNodeWithTag(RESULT_STAGE_DEFAULT_AVATAR_TAG).getUnclippedBoundsInRoot()
        assertNear(180.dp, avatar.bottom - avatar.top, "아바타 높이")
        assertNear(266.dp, avatar.bottom, "발끝")
        assertEquals(0, compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + "JUMP").fetchSemanticsNodes().size)
    }

    @Test
    fun `전신 정지 그림 칸이 비어도(미리보기) 기본 서기 그림이 같은 자리에 선다`() {
        show(slot = null)
        val avatar = compose.onNodeWithTag(RESULT_STAGE_DEFAULT_AVATAR_TAG).getUnclippedBoundsInRoot()
        assertNear(180.dp, avatar.bottom - avatar.top, "아바타 높이")
        assertNear(266.dp, avatar.bottom, "발끝")
    }

    @Test
    fun `시트 위가 300dp다`() {
        show()
        assertNear(300.dp, compose.onNodeWithTag(RESULT_SHEET_TAG).getUnclippedBoundsInRoot().top, "시트 위")
    }

    @Test
    fun `배경 키는 꾸미기가 열렸을 때만 쓴다`() {
        assertEquals("PARIS", resultBackgroundKey(state.myAvatar, isAvatarOpen = true))
        assertEquals(null, resultBackgroundKey(state.myAvatar, isAvatarOpen = false))
        assertEquals(null, resultBackgroundKey(null, isAvatarOpen = true))
    }

    @Test
    fun `불러오는 중과 실패에는 무대가 없다`() {
        show(state = RunResultUiState(isLoading = true))
        compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).assertDoesNotExist()
        compose.onNodeWithTag(RESULT_SHEET_TAG).assertDoesNotExist()
    }
}
