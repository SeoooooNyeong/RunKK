package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSpeed
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSubject
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunStanding
import com.a207.runkk.app.domain.live.CrewRunRelay
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 트랙의 대상, 크기와 방향을 보존하고, 멈춤 판정과 페이스 빠르기를 함께 검사한다(D-856).
 */
@RunWith(RobolectricTestRunner::class)
class CrewRunAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var pose: AvatarSlotPose? = null
    private var left: Boolean? = null
    private var speed: Float? = null
    private val probe: @Composable () -> Unit = {
        if (LocalSlotAvatarRunSubject.current.isMine) {
            pose = LocalSlotAvatarPose.current
            left = LocalSlotAvatarFacingLeft.current
            speed = LocalSlotAvatarRunSpeed.current
            Box(Modifier.fillMaxSize().testTag("crew-avatar-probe"))
        }
    }

    private val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))

    @Test
    fun `가장 가까운 크루원도 거리가 늘지 않으면 서고 다시 움직이면 달린다`() {
        var meters by mutableStateOf(90)
        var otherPose: AvatarSlotPose? = null
        val otherProbe: @Composable () -> Unit = {
            if (!LocalSlotAvatarRunSubject.current.isMine) {
                val value = LocalSlotAvatarPose.current
                SideEffect { otherPose = value }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val standing = CrewRunStanding.of(
                88,
                listOf(CrewRunStanding.Mate(1, "바람처럼", meters, CrewRunRelay.State.RUNNING))
            )
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(otherProbe))) {
                RunkkTheme(reduceMotion = true) {
                    CrewRunScreen(running().copy(standing = standing), CrewRunActions())
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, otherPose)
        compose.mainClock.advanceTimeBy(3_100)
        assertEquals(AvatarSlotPose.STAND, otherPose)
        meters = 94
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, otherPose)
    }

    private fun running(distance: Int = 88, pace: Pace? = Pace(300)) = CrewRunUiState(
        postId = 31,
        distance = Distance(5_000),
        phase = CrewRunPhase.RUNNING,
        mine = CrewRunProgress.ZERO.copy(distance = Distance(distance.toLong()), currentPace = pace, route = westward)
    )

    @Test
    fun `크루런 중 움직이면 RUN과 트랙 방향, 페이스 빠르기를 넘기고 내가 멈추면 STAND다`() {
        var state by mutableStateOf(running())
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        compose.onNodeWithTag("crew-avatar-probe").assertWidthIsEqualTo(105.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(false, left, "서쪽으로 달려도 트랙에서는 오른쪽을 본다")
        assertEquals(1.2f, checkNotNull(speed), 0.001f)

        state = state.copy(mine = state.mine.copy(isPaused = true))
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }

    @Test
    fun `가장 가까운 크루원 한 명만 대기실 모습으로 칸에 넘긴다(D-성-259, D-성-261)`() {
        val look = RunnerLook(Gender.FEMALE, 12, null, 31, bodyVariant = 1, runAmount = 42, faceKey = "default:FOX")
        val subjects = mutableListOf<RunAvatarSubject>()
        val subjectProbe: @Composable () -> Unit = {
            val subject = LocalSlotAvatarRunSubject.current
            SideEffect { if (subject !in subjects) subjects += subject }
        }
        val mates = listOf(
            CrewRunStanding.Mate(1, "바람처럼", 412, CrewRunRelay.State.RUNNING),
            CrewRunStanding.Mate(2, "달빛", 380, CrewRunRelay.State.RUNNING),
            CrewRunStanding.Mate(3, "한강달림", 900, CrewRunRelay.State.RUNNING)
        )
        val state = CrewRunUiState(
            postId = 31,
            distance = Distance(5_000),
            phase = CrewRunPhase.RUNNING,
            relay = listOf(
                RelayRow("바람처럼", Distance(412), shownGender = Gender.FEMALE, memberId = 1),
                RelayRow("달빛", Distance(380), memberId = 2),
                RelayRow("한강달림", Distance(900), memberId = 3),
                RelayRow("나", Distance(400), isMe = true, memberId = 7)
            ),
            mine = CrewRunProgress.ZERO.copy(distance = Distance(400)),
            looks = mapOf(1L to look),
            standing = CrewRunStanding.of(400, mates)
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(subjectProbe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        compose.waitForIdle()
        assertEquals(
            setOf(RunAvatarSubject.Mine, RunAvatarSubject(isMine = false, look = look.toSlot(), isFemale = true)),
            subjects.toSet(),
            "Rive 칸은 나와 가장 가까운 크루원 둘뿐이다"
        )
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
    fun `출발 뒤 첫 거리가 5초 늦어도 RUN이고 첫 거리 뒤 3초 그대로면 STAND다(S15P31A207-335 3차)`() {
        var latest: AvatarSlotPose? = null
        val startProbe: @Composable () -> Unit = {
            if (LocalSlotAvatarRunSubject.current.isMine) {
                val value = LocalSlotAvatarPose.current
                speed = LocalSlotAvatarRunSpeed.current
                SideEffect { latest = value }
            }
        }
        var state by mutableStateOf(running(distance = 0, pace = null))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(startProbe))) {
                RunkkTheme(reduceMotion = true) { CrewRunScreen(state, CrewRunActions()) }
            }
        }
        // 16ms 프레임마다 내 자세를 적는다. GPS 첫 거리는 출발 5초 뒤에 온다(에뮬레이터 녹화: 5.7초).
        val rows = mutableListOf<Pair<Long, AvatarSlotPose?>>()
        var t = 0L
        fun runUntil(end: Long) {
            while (t < end) {
                compose.mainClock.advanceTimeByFrame()
                t += FRAME_MILLIS
                rows += t to latest
            }
        }
        runUntil(FIRST_DISTANCE_AT)
        assertEquals(
            emptyList(),
            rows.filter { it.second != AvatarSlotPose.RUN }.map { it.first },
            "첫 거리 전(거리 0)에는 정면으로 서지 않는다(ms)"
        )
        assertEquals(1f, checkNotNull(speed), 0.001f, "페이스를 모르면 빠르기는 기준값이다")

        state = state.copy(mine = state.mine.copy(distance = Distance(10)))
        Snapshot.sendApplyNotifications()
        runUntil(FIRST_DISTANCE_AT + 4_000)
        val afterFirst = rows.filter { it.first > FIRST_DISTANCE_AT }
        val standAt = afterFirst.first { it.second == AvatarSlotPose.STAND }.first - FIRST_DISTANCE_AT
        assertTrue(standAt in 3_000L..3_100L, "첫 거리 뒤 3초 그대로면 선다: +$standAt ms")
        assertTrue(afterFirst.filter { it.first - FIRST_DISTANCE_AT < 3_000 }.all { it.second == AvatarSlotPose.RUN })
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val FIRST_DISTANCE_AT = 5_008L
    }
}
