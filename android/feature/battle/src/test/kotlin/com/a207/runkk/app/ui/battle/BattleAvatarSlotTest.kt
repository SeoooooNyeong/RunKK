package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSubject
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import com.a207.runkk.app.core.navigation.SlotKey
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 대결 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122]): RUNNING이면 RUN(일시정지 없음), 마감(CLOSING)
 * 등 그 밖은 STAND이고 내 칸은 88dp다. 트랙 보기([임시:FR-성-27])에서는 나와 상대 모두 진행 방향(오른쪽)을 보므로 지도 위 진행
 * 방향 원값을 넘기지 않는다. 트랙 안의 상대는 같은 칸에 상대 대상으로 그린다([임시:D-성-259]).
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
    fun `대결 중이면 RUN을 넘기고 마감 중이면 STAND다(트랙에서는 늘 오른쪽을 본다)`() {
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
        assertEquals(false, left, "서쪽으로 달려도 트랙에서는 오른쪽을 본다")

        state = state.copy(phase = BattlePhase.CLOSING)
        compose.waitForIdle()
        assertEquals(AvatarSlotPose.STAND, pose)
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
}
