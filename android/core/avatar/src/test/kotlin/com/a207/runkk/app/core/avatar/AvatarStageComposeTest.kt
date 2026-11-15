package com.a207.runkk.app.core.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 로딩 표시 규칙(D-738: 100ms 안에 준비되면 그리지 않음, 아니면 지름 32dp 원형 표시, 준비되면 200ms 페이드), 바닥 배치
 * (D-739 4.4: 아트보드 상자가 칸 안 계산 자리), 달리기 방향 유지(D-765: 같은 방향이 2초 이어질 때만 뒤집음).
 */
// 칸 350x390dp가 화면 안에 들어가게 한다(Robolectric 기본 화면은 너비 320dp).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AvatarStageComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `100ms 안에 준비되면 로딩 표시를 그리지 않는다`() {
        compose.mainClock.autoAdvance = false
        var isReady by mutableStateOf(false)
        compose.setContent { AvatarLoadingPlaceholder(isReady = isReady, color = Color.Gray) }

        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertDoesNotExist()
        changeState { isReady = true }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertDoesNotExist()
    }

    @Test
    fun `100ms가 지나면 32dp 표시를 보이고 준비되면 200ms 페이드 뒤 거둔다`() {
        compose.mainClock.autoAdvance = false
        var isReady by mutableStateOf(false)
        compose.setContent { AvatarLoadingPlaceholder(isReady = isReady, color = Color.Gray) }

        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        changeState { isReady = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertDoesNotExist()
    }

    @Test
    fun `페이드 중에 다시 준비 전으로 돌아가면 기다리지 않고 바로 불투명하게 보인다`() {
        compose.mainClock.autoAdvance = false
        var isReady by mutableStateOf(false)
        val alpha = Animatable(1f)
        compose.setContent { AvatarLoadingPlaceholder(isReady = isReady, color = Color.Gray, alpha = alpha) }

        compose.mainClock.advanceTimeBy(150)
        changeState { isReady = true }
        compose.mainClock.advanceTimeBy(100)
        assertTrue(alpha.value in 0.01f..0.99f, "페이드 중간: ${alpha.value}")
        changeState { isReady = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertExists()
        assertEquals(1f, alpha.value, "보이던 표시는 지연 없이 되돌린다")

        changeState { isReady = true }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag(AVATAR_LOADING_TAG).assertDoesNotExist()
    }

    @Test
    fun `아트보드 상자는 바닥 규격 계산 자리에 놓인다`() {
        compose.setContent {
            Box(Modifier.size(350.dp, 390.dp)) {
                AvatarStageFrame(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = null,
                    backdropSize = androidx.compose.ui.geometry.Size(1080f, 1200f),
                    darkTheme = false
                ) {
                    Box(Modifier.fillMaxSize().testTag("artboard"))
                }
            }
        }
        val layout = AvatarStageLayout.of(350f, 390f, 1080f, 1200f)
        val bounds = compose.onNodeWithTag("artboard").getBoundsInRoot()
        assertNear(layout.artboardLeft, bounds.left.value, "왼쪽")
        assertNear(layout.artboardTop, bounds.top.value, "위")
        assertNear(layout.artboardWidth, bounds.width.value, "너비")
        assertNear(layout.artboardHeight, bounds.height.value, "높이")
        assertNear(layout.floorY, bounds.top.value + RiveAvatarGeometry.FOOT_LINE * bounds.height.value, "발끝 줄 = 바닥선")
    }

    @Test
    fun `러닝 중 칸처럼 아트보드가 칸보다 커도 계산한 크기 그대로 둔다`() {
        compose.setContent {
            Box(Modifier.size(88.dp)) {
                AvatarStageFrame(modifier = Modifier.fillMaxSize(), backdrop = null, darkTheme = true) {
                    Box(Modifier.fillMaxSize().testTag("artboard"))
                }
            }
        }
        val bounds = compose.onNodeWithTag("artboard").getUnclippedBoundsInRoot()
        assertNear(102.96f, bounds.height.value, "높이")
        assertNear(82.37f, bounds.width.value, "너비")
        assertNear(0f, bounds.top.value, "위 끝 = 칸 위 끝")
    }

    @Test
    fun `달리기 방향은 같은 방향이 2초 이어질 때만 뒤집고 그 전에 돌아오면 무시한다`() {
        compose.mainClock.autoAdvance = false
        var raw by mutableStateOf(false)
        var shown = false
        compose.setContent { shown = rememberRunFacingLeft(raw) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(false, shown, "처음은 들어온 값")

        changeState { raw = true }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(false, shown, "1.5초는 아직")
        changeState { raw = false }
        compose.mainClock.advanceTimeBy(1_000)
        changeState { raw = true }
        compose.mainClock.advanceTimeBy(1_900)
        assertEquals(false, shown, "흔들리면 처음부터 다시 센다")
        compose.mainClock.advanceTimeBy(200)
        assertEquals(true, shown, "2초 이어지면 뒤집는다")
    }

    /** 시험 스레드에서 바꾼 상태를 곧바로 알린다(멈춘 시계에서는 전역 스냅샷 알림이 다음 일까지 밀린다). */
    private fun changeState(block: () -> Unit) = Snapshot.withMutableSnapshot(block)

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertTrue(abs(expected - actual) <= 1f, "$message: 기대 $expected, 실제 $actual")
    }
}
