package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.a207.runkk.app.domain.crew.CrewRunProgress
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 크루런 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122]): RUNNING이고 내가 멈추지 않았으면 RUN, 진행
 * 방향 원값을 넘기고 내 칸만 88dp다(크루원 그림은 그대로).
 */
@RunWith(RobolectricTestRunner::class)
class CrewRunAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var pose: AvatarSlotPose? = null
    private var left: Boolean? = null
    private val probe: @Composable () -> Unit = {
        pose = LocalSlotAvatarPose.current
        left = LocalSlotAvatarFacingLeft.current
        Box(Modifier.fillMaxSize().testTag("crew-avatar-probe"))
    }

    @Test
    fun `크루런 중이면 RUN과 진행 방향을 넘기고 내가 멈추면 STAND다`() {
        val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))
        var state by mutableStateOf(
            CrewRunUiState(
                postId = 31,
                distance = Distance(5_000),
                phase = CrewRunPhase.RUNNING,
                mine = CrewRunProgress.ZERO.copy(distance = Distance(88), route = westward)
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        compose.onNodeWithTag("crew-avatar-probe").assertWidthIsEqualTo(88.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(true, left)

        state = state.copy(mine = state.mine.copy(isPaused = true))
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }
}
