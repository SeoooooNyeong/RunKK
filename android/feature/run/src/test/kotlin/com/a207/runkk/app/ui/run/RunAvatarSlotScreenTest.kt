package com.a207.runkk.app.ui.run

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.run.RunProgress
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 자유런 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122], [임시:D-성-133]): 측정 중(일시정지 아님)이면
 * RUN, 그 밖은 STAND이고, 진행 방향은 `rememberHeadingLeft` 원값을 그대로 넘긴다. 칸은 88dp다.
 */
@RunWith(RobolectricTestRunner::class)
class RunAvatarSlotScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var pose: AvatarSlotPose? = null
    private var left: Boolean? = null
    private val probe: @Composable () -> Unit = {
        pose = LocalSlotAvatarPose.current
        left = LocalSlotAvatarFacingLeft.current
        Box(Modifier.fillMaxSize().testTag("run-avatar-probe"))
    }

    /** 서쪽으로 약 88m(경도가 준다 → 왼쪽). */
    private val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))

    @Test
    fun `측정 중이면 RUN과 진행 방향 원값을 넘기고 일시정지면 STAND다`() {
        var state by mutableStateOf(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = RunProgress.INITIAL.copy(distance = Distance(88)),
                route = westward
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { RunScreen(state, RunActions()) }
            }
        }
        compose.onNodeWithTag("run-avatar-probe").assertWidthIsEqualTo(88.dp).assertHeightIsEqualTo(88.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(true, left)

        state = state.copy(progress = state.progress.copy(isPaused = true))
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }

    @Test
    fun `경로가 없으면 칸을 가운데 88dp로 둔다`() {
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { RunScreen(RunUiState(phase = RunPhase.RUNNING), RunActions()) }
            }
        }
        compose.onNodeWithTag("run-avatar-probe").assertWidthIsEqualTo(88.dp)
        assertEquals(false, left, "처음은 오른쪽")
    }

    @Test
    fun `DR-성-14 위치가 10번 갱신되어도 칸 내용은 한 번만 만든다`() {
        var created = 0
        val counting: @Composable () -> Unit = {
            DisposableEffect(Unit) {
                created++
                onDispose {}
            }
            Box(Modifier.fillMaxSize())
        }
        val points = mutableListOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0002))
        var state by mutableStateOf(RunUiState(phase = RunPhase.RUNNING, route = RouteLine(points.toList())))
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(counting))) {
                RunkkTheme(reduceMotion = true) { RunScreen(state, RunActions()) }
            }
        }
        repeat(10) { i ->
            points += GeoPoint(37.5, 127.0004 + i * 0.0002)
            state =
                state.copy(
                    route = RouteLine(points.toList()),
                    progress = state.progress.copy(distance = Distance(20L * (i + 2)))
                )
            compose.waitForIdle()
        }

        assertEquals(1, created)
    }
}
