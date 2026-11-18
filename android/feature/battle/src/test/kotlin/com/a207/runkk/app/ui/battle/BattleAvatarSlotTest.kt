package com.a207.runkk.app.ui.battle

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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 트랙의 대상, 크기와 방향을 보존하고, 멈춤 판정과 페이스 빠르기를 함께 검사한다(D-856).
 */
@RunWith(RobolectricTestRunner::class)
class BattleAvatarSlotTest {
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
            Box(Modifier.fillMaxSize().testTag("battle-avatar-probe"))
        }
    }

    private val westward = RouteLine(listOf(GeoPoint(37.5, 127.001), GeoPoint(37.5, 127.0)))

    private fun running(distance: Int = 88, pace: Pace? = Pace(300)) = BattleRunningUiState(
        battleId = 9,
        opponentName = "바람처럼",
        phase = BattlePhase.RUNNING,
        gapMeters = 10,
        myDistance = Distance(distance.toLong()),
        currentPace = pace,
        route = westward
    )

    @Test
    fun `상대는 첫 거리 수신에도 RUN을 유지하고 정지가 확인되면 서며 다시 움직이면 달린다`() {
        var state by mutableStateOf(running().copy(opponentDistance = null))
        var opponentPose: AvatarSlotPose? = null
        val observed = mutableListOf<AvatarSlotPose>()
        val opponentProbe: @Composable () -> Unit = {
            if (!LocalSlotAvatarRunSubject.current.isMine) {
                val value = LocalSlotAvatarPose.current
                SideEffect {
                    opponentPose = value
                    observed += value
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(opponentProbe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.mainClock.advanceTimeBy(3_500)
        assertEquals(AvatarSlotPose.RUN, opponentPose, "첫 거리 전에는 서기로 바꾸지 않는다")

        state = state.copy(opponentDistance = Distance.ZERO)
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(setOf(AvatarSlotPose.RUN), observed.toSet(), "첫 값이 0이어도 자세를 다시 준비하지 않는다")
        compose.mainClock.advanceTimeBy(3_100)
        assertEquals(AvatarSlotPose.STAND, opponentPose)

        state = state.copy(opponentDistance = Distance(6))
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, opponentPose)
    }

    @Test
    fun `대결 중 움직이면 RUN과 트랙 방향, 페이스 빠르기를 넘기고 마감 중이면 STAND다`() {
        var state by mutableStateOf(running())
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.onNodeWithTag("battle-avatar-probe").assertWidthIsEqualTo(105.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(false, left, "서쪽으로 달려도 트랙에서는 오른쪽을 본다")
        assertEquals(1.2f, checkNotNull(speed), 0.001f)

        state = state.copy(phase = BattlePhase.CLOSING)
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
    }

    @Test
    fun `상대 진행 대기에서 격차로 바뀌어도 트랙 카드 위치와 높이가 같다`() {
        var state by mutableStateOf(BattleRunningUiState(battleId = 9, phase = BattlePhase.RUNNING))
        compose.setContent { RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) } }
        val before = compose.onNodeWithTag(BATTLE_TRACK_TAG).getUnclippedBoundsInRoot()
        state = state.copy(gapMeters = -3)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag(BATTLE_TRACK_TAG).getUnclippedBoundsInRoot())
    }

    @Test
    fun `진행 화면 왼쪽 위에 종류와 목표 거리 알약을 둔다`() {
        var kind by mutableStateOf(OpponentKind.RANDOM)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                BattleRunningScreen(
                    BattleRunningUiState(
                        battleId = 9,
                        phase = BattlePhase.RUNNING,
                        distance = Distance(5_000),
                        kind = kind
                    ),
                    BattleRunningActions()
                )
            }
        }
        compose.onNodeWithText("배틀런, 5km").assertExists()
        kind = OpponentKind.SELF
        compose.onNodeWithText("셀프런, 5km").assertExists()
        kind = OpponentKind.CELEBRITY
        compose.onNodeWithText("셀럽런, 5km").assertExists()
    }

    @Test
    fun `트랙 안의 상대는 받은 모습을 상대 대상으로 칸에 넘긴다(D-성-259)`() {
        val look = RunnerLook(Gender.FEMALE, 12, null, 31, bodyVariant = 1, runAmount = 42, faceKey = "head:7:ab12")
        val subjects = mutableListOf<RunAvatarSubject>()
        val subjectProbe: @Composable () -> Unit = {
            val subject = LocalSlotAvatarRunSubject.current
            SideEffect { if (subject !in subjects) subjects += subject }
        }
        val state = BattleRunningUiState(
            battleId = 9,
            opponentName = "바람처럼",
            phase = BattlePhase.RUNNING,
            gapMeters = -6,
            opponentLook = look
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(subjectProbe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.waitForIdle()
        assertEquals(
            setOf(RunAvatarSubject.Mine, RunAvatarSubject(isMine = false, look = look.toSlot(), isFemale = true)),
            subjects.toSet(),
            "Rive 칸은 나와 상대 둘뿐이다"
        )
    }

    @Test
    fun `모습을 받지 못한 상대는 성별 기본 모습으로, 배치고사 상대는 칸 없이 물음표로 그린다(D-성-253)`() {
        val subjects = mutableListOf<RunAvatarSubject>()
        val subjectProbe: @Composable () -> Unit = {
            val subject = LocalSlotAvatarRunSubject.current
            SideEffect { if (subject !in subjects) subjects += subject }
        }
        var state by mutableStateOf(
            BattleRunningUiState(
                battleId = 9,
                opponentName = "고스트",
                phase = BattlePhase.RUNNING,
                gapMeters = -6,
                opponentGender = Gender.FEMALE
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(subjectProbe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.waitForIdle()
        assertEquals(RunAvatarSubject(isMine = false, look = null, isFemale = true), subjects.single { !it.isMine })

        subjects.clear()
        state = state.copy(placementRound = 1, opponentGender = null)
        compose.waitForIdle()
        assertEquals(emptyList(), subjects.filter { !it.isMine }, "배치고사 상대는 아바타 칸을 쓰지 않는다")
        compose.onNodeWithText("?", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `대결 중이어도 거리가 3초 늘지 않으면 STAND이고 다시 늘면 RUN이다(D-856)`() {
        var state by mutableStateOf(running())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(probe))) {
                RunkkTheme(reduceMotion = true) { BattleRunningScreen(state, BattleRunningActions()) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, pose)

        compose.mainClock.advanceTimeBy(3_100)
        assertEquals(AvatarSlotPose.STAND, pose, "3초 동안 거리가 그대로면 멈춘 것")

        state = state.copy(myDistance = Distance(95))
        // 시계를 손으로 돌리는 동안에는 바뀐 상태를 직접 알린다(waitForIdle을 쓰지 않으므로).
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(AvatarSlotPose.RUN, pose, "다시 움직이면 달린다")
    }

    @Test
    fun `출발하면 거리·페이스를 기다리지 않고 첫 프레임부터 RUN이고 3초 동안 거리가 그대로면 STAND다(S15P31A207-335 2차)`() {
        val observed = mutableListOf<AvatarSlotPose>()
        val startProbe: @Composable () -> Unit = {
            if (LocalSlotAvatarRunSubject.current.isMine) {
                val value = LocalSlotAvatarPose.current
                speed = LocalSlotAvatarRunSpeed.current
                SideEffect { observed += value }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.RUN_AVATAR to listOf(startProbe))) {
                RunkkTheme(reduceMotion = true) {
                    BattleRunningScreen(running(distance = 0, pace = null), BattleRunningActions())
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_500)
        assertEquals(setOf(AvatarSlotPose.RUN), observed.toSet(), "출발 직후 정면 서기를 거치지 않는다")
        assertEquals(1f, checkNotNull(speed), 0.001f, "페이스를 모르면 빠르기는 기준값이다")

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(AvatarSlotPose.STAND, observed.last(), "3초 동안 거리가 그대로면 멈춘 것")
    }
}
