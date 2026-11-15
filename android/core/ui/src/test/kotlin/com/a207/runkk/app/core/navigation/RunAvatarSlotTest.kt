package com.a207.runkk.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.a207.runkk.app.core.designsystem.MY_MARKER_TAG
import com.a207.runkk.app.core.designsystem.RouteCanvas
import com.a207.runkk.app.core.designsystem.RouteMarker
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 러닝 중 아바타 칸(design-oo 05-30 D-732·D-733·D-753·D-765): 측정 화면은 칸에 동작과 진행 방향
 * 원값만 넘기고, 칸이 비면 지금 그림을 그린다. 내 마커만 88dp로 키울 수 있다.
 */
@RunWith(RobolectricTestRunner::class)
class RunAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var seenPose: AvatarSlotPose? = null
    private var seenLeft: Boolean? = null

    private val probe: @Composable () -> Unit = {
        seenPose = LocalSlotAvatarPose.current
        seenLeft = LocalSlotAvatarFacingLeft.current
        Box(Modifier.fillMaxSize().testTag("slot"))
    }

    @Test
    fun `칸이 비면 지금 그림을 그린다`() {
        compose.setContent {
            RunAvatarSlot(AvatarSlotPose.RUN, facingLeft = true) { Box(Modifier.testTag("fallback")) }
        }
        compose.onNodeWithTag("fallback").assertExists()
        compose.onNodeWithTag("slot").assertDoesNotExist()
    }

    @Test
    fun `칸이 있으면 동작과 진행 방향 원값을 칸에 넘기고 지금 그림은 그리지 않는다`() {
        var pose by mutableStateOf(AvatarSlotPose.STAND)
        var left by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunAvatarSlot(pose, facingLeft = left) { Box(Modifier.testTag("fallback")) }
            }
        }
        compose.onNodeWithTag("slot").assertExists()
        compose.onNodeWithTag("fallback").assertDoesNotExist()
        assertEquals(AvatarSlotPose.STAND, seenPose)
        assertEquals(false, seenLeft)

        pose = AvatarSlotPose.RUN
        left = true
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.RUN, seenPose)
        assertEquals(true, seenLeft)
    }

    @Test
    fun `상대 대상이면 상대 모습으로 그린다`() {
        val look = SlotRunnerLook(isFemale = true, topItemId = 12, shoesItemId = 31, bodyVariant = 1, runAmount = 42)
        var seen: RunAvatarSubject? = null
        val subjectProbe: @Composable () -> Unit = { seen = LocalSlotAvatarRunSubject.current }
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(subjectProbe))) {
                RunAvatarSlot(AvatarSlotPose.RUN, facingLeft = false, subject = RunAvatarSubject.other(look, null)) {}
            }
        }
        assertEquals(RunAvatarSubject(isMine = false, look = look, isFemale = true), seen)
    }

    @Test
    fun `모습을 받지 못한 상대는 표시 성별만 넘긴다`() {
        assertEquals(RunAvatarSubject(isMine = false, look = null, isFemale = true), RunAvatarSubject.other(null, true))
        assertEquals(RunAvatarSubject(isMine = false, look = null, isFemale = null), RunAvatarSubject.other(null, null))
    }

    @Test
    fun `기본 대상은 지금처럼 내 아바타다`() {
        var seen: RunAvatarSubject? = null
        var outside: RunAvatarSubject? = null
        val subjectProbe: @Composable () -> Unit = { seen = LocalSlotAvatarRunSubject.current }
        compose.setContent {
            outside = LocalSlotAvatarRunSubject.current
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(subjectProbe))) {
                RunAvatarSlot(AvatarSlotPose.RUN, facingLeft = false) {}
            }
        }
        assertEquals(RunAvatarSubject.Mine, seen)
        assertEquals(RunAvatarSubject.Mine, outside, "칸 밖에서도 내 아바타다")
        assertEquals(true, RunAvatarSubject.Mine.isMine)
    }

    @Test
    fun `칸 기여가 있는지 화면이 안다`() {
        var empty: Boolean? = null
        var filled: Boolean? = null
        compose.setContent {
            empty = isSlotFilled(SlotKey.RUN_AVATAR)
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.HOME_AVATAR to listOf(probe))) {
                filled = isSlotFilled(SlotKey.HOME_AVATAR)
            }
        }
        assertEquals(false, empty)
        assertEquals(true, filled)
    }

    @Test
    fun `격자 경로의 내 마커만 88dp로 키운다`() {
        val start = GeoPoint(37.5665, 126.9780)
        val route = RouteLine(listOf(start, GeoPoint(37.5665, 126.9790)))
        compose.setContent {
            RouteCanvas(
                route = route,
                markers = listOf(
                    RouteMarker(start, tag = "other", figure = { Box(Modifier.fillMaxSize().testTag("other-figure")) }),
                    RouteMarker(
                        route.points.last(),
                        tag = MY_MARKER_TAG,
                        size = 88.dp,
                        figure = { Box(Modifier.fillMaxSize().testTag("my-figure")) }
                    )
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
        compose.onNodeWithTag("my-figure").assertWidthIsEqualTo(88.dp).assertHeightIsEqualTo(88.dp)
        compose.onNodeWithTag("other-figure").assertWidthIsEqualTo(72.dp)
    }
}
