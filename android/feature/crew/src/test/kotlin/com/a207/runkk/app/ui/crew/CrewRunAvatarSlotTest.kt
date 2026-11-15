package com.a207.runkk.app.ui.crew

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
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunStanding
import com.a207.runkk.app.domain.live.CrewRunRelay
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 크루런 측정 화면의 내 아바타 칸(design-oo 05-30 7절, [임시:D-성-121], [임시:D-성-122]): RUNNING이고 내가 멈추지 않았으면 RUN, 진행
 * 보이는 내 칸은 카드 높이에 맞춰 최대 105dp다. 트랙 보기([임시:FR-성-30])에서는 나와 크루원 모두 진행 방향(오른쪽)을 보므로 지도 위 진행 방향 원값을 넘기지 않는다.
 * 가장 가까운 크루원은 같은 칸에 그 크루원 대상으로 그린다([임시:D-성-259]).
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
    fun `크루런 중이면 RUN을 넘기고 내가 멈추면 STAND다(트랙에서는 늘 오른쪽을 본다)`() {
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
        compose.onNodeWithTag("crew-avatar-probe").assertWidthIsEqualTo(105.dp)
        assertEquals(AvatarSlotPose.RUN, pose)
        assertEquals(false, left, "서쪽으로 달려도 트랙에서는 오른쪽을 본다")

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
}
