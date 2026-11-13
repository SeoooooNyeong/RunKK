package com.a207.runkk.app.ui.battle

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
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 대결 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122]): RUNNING이면 RUN(일시정지 없음), 마감(CLOSING)
 * 등 그 밖은 STAND이고, 진행 방향 원값을 넘기며 내 칸만 88dp다(상대 그림은 그대로).
 */
@RunWith(RobolectricTestRunner::class)
class BattleAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var pose: AvatarSlotPose? = null
    private var left: Boolean? = null
    private val probe: @Composable () -> Unit = {
        pose = LocalSlotAvatarPose.current
        left = LocalSlotAvatarFacingLeft.current
        Box(Modifier.fillMaxSize().testTag("battle-avatar-probe"))
    }

    @Test
    fun `대결 중이면 RUN과 진행 방향을 넘기고 마감 중이면 STAND다`() {
        val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))
        var state by mutableStateOf(
            BattleRunningUiState(
                battleId = 9,
                opponentName = "바람처럼",
                phase = BattlePhase.RUNNING,
                gapMeters = 10,
                myDistance = Distance(88),
                route = westward
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.onNodeWithTag("battle-avatar-probe").assertWidthIsEqualTo(88.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(true, left)

        state = state.copy(phase = BattlePhase.CLOSING)
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }
}
