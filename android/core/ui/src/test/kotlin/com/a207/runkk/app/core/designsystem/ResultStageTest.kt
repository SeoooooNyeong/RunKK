package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 결과 무대 호스트([임시:D-성-281], [임시:D-성-309]): 배경 그림과 아바타 칸을 [ResultStageLayout] 좌표에 놓는다. 좌표는 물리 화면 위쪽
 * 끝 기준이라, 상태 표시줄 아래에서 시작하는 화면 안에서는 그 높이([topInset])만큼 위로 올려 놓는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h891dp")
class ResultStageTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, topInset: Dp = 0.dp, backgroundKey: String? = null) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.width(width)) {
                    ResultStage(backgroundKey, topInset = topInset) { Box(it.testTag("avatar")) }
                }
            }
        }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected $expected, actual $actual")

    @Test
    fun `폭 360은 아바타 칸 높이 180 너비 144에 발끝이 266이다`() {
        val width = 360.dp
        show(width)
        val bounds = compose.onNodeWithTag("avatar").getUnclippedBoundsInRoot()
        assertNear(180.dp, bounds.bottom - bounds.top, "높이")
        assertNear(266.dp, bounds.bottom, "발끝")
        assertNear(144.dp, bounds.right - bounds.left, "너비")
        assertNear((width - 144.dp) / 2, bounds.left, "왼쪽")
    }

    @Test
    fun `폭 320은 발끝 266 높이 180이고 그림 바닥이 300이다`() {
        show(320.dp)
        val avatar = compose.onNodeWithTag("avatar").getUnclippedBoundsInRoot()
        assertNear(266.dp, avatar.bottom, "발끝")
        assertNear(180.dp, avatar.bottom - avatar.top, "높이")
        assertNear(88.dp, avatar.left, "왼쪽")
        val backdrop = compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).getUnclippedBoundsInRoot()
        assertNear(300.dp, backdrop.bottom, "그림 바닥")
        assertNear((-10).dp, backdrop.left, "그림 왼쪽")
        assertNear(340.dp, backdrop.right - backdrop.left, "그림 너비")
    }

    @Test
    fun `폭 600은 발끝 266 높이 180이고 그림이 가로를 채운다`() {
        show(600.dp, backgroundKey = "PARIS")
        val avatar = compose.onNodeWithTag("avatar").getUnclippedBoundsInRoot()
        assertNear(266.dp, avatar.bottom, "발끝")
        assertNear(180.dp, avatar.bottom - avatar.top, "높이")
        assertNear(228.dp, avatar.left, "왼쪽")
        val backdrop = compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).getUnclippedBoundsInRoot()
        assertNear(600.dp, backdrop.right - backdrop.left, "그림 너비")
        assertNear(352.dp, backdrop.bottom, "그림 바닥")
        // 그림의 트랙 윗선(아래에서 18%)이 232다.
        assertNear(232.dp, backdrop.bottom - (backdrop.bottom - backdrop.top) * 0.18f, "트랙 윗선")
    }

    @Test
    fun `상태 표시줄 높이만큼 위로 올려 물리 화면 기준 232·266을 지킨다(D-성-309)`() {
        show(360.dp, topInset = 24.dp)
        val avatar = compose.onNodeWithTag("avatar").getUnclippedBoundsInRoot()
        assertNear(242.dp, avatar.bottom, "발끝(화면 안 좌표 = 266 − 24)")
        assertNear(180.dp, avatar.bottom - avatar.top, "높이")
        val backdrop = compose.onNodeWithTag(RESULT_STAGE_BACKDROP_TAG).getUnclippedBoundsInRoot()
        assertNear((-120).dp, backdrop.top, "그림 위(−96 − 24)")
    }
}
