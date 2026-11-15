package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSpeed
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.crew.CrewRunProgress
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 크루런 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122], D-856): RUNNING이고 내가 움직이는 중이면
 * RUN, 내가 일시정지했거나 거리가 3초 늘지 않거나(멈춤) 페이스를 모르면 STAND다. 달리기 빠르기는 자유런·대결과 같이 내 현재 페이스에
 * 비례하고(runMotionSpeed), 진행 방향 원값을 넘기며 내 칸만 88dp다(크루원 그림은 그대로). PM 2026-11-15.
 */
@RunWith(RobolectricTestRunner::class)
class CrewRunAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var pose: AvatarSlotPose? = null
    private var left: Boolean? = null
    private var speed: Float? = null
    private val probe: @Composable () -> Unit = {
        pose = LocalSlotAvatarPose.current
        left = LocalSlotAvatarFacingLeft.current
        speed = LocalSlotAvatarRunSpeed.current
        Box(Modifier.fillMaxSize().testTag("crew-avatar-probe"))
    }

    private val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))

    private fun running(distance: Int = 88, pace: Pace? = Pace(300)) = CrewRunUiState(
        postId = 31,
        distance = Distance(5_000),
        phase = CrewRunPhase.RUNNING,
        mine = CrewRunProgress.ZERO.copy(distance = Distance(distance.toLong()), currentPace = pace, route = westward)
    )

    @Test
    fun `크루런 중 움직이면 RUN과 진행 방향, 페이스 빠르기를 넘기고 내가 멈추면 STAND다`() {
        var state by mutableStateOf(running())
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        compose.onNodeWithTag("crew-avatar-probe").assertWidthIsEqualTo(88.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(true, left)
        // 5:00/km는 기준(6:00/km)보다 1.2배 빠르다(자유런·대결과 같은 runMotionSpeed)
        assertEquals(1.2f, checkNotNull(speed), 0.001f)

        state = state.copy(mine = state.mine.copy(isPaused = true))
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }

    @Test
    fun `크루런 중이어도 거리가 3초 늘지 않으면 STAND이고 다시 늘면 RUN이다(D-856)`() {
        var state by mutableStateOf(running())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, pose)

        compose.mainClock.advanceTimeBy(3_100)
        assertEquals(AvatarSlotPose.STAND, pose, "3초 동안 거리가 그대로면 멈춘 것")

        state = state.copy(mine = state.mine.copy(distance = Distance(95)))
        // 시계를 손으로 돌리는 동안에는 바뀐 상태를 직접 알린다(waitForIdle을 쓰지 않으므로).
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, pose, "다시 움직이면 달린다")
    }

    @Test
    fun `크루런 중이어도 페이스를 모르면 STAND이고 빠르기는 기준값이다`() {
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(running(pace = null), CrewRunActions()) }
            }
        }
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
        assertEquals(1f, checkNotNull(speed), 0.001f)
    }
}
