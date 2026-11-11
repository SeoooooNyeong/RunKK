package com.a207.runkk.app.ui.home

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 홈 아바타 칸(design-oo 05-30 2절, [임시:D-성-100], [임시:D-성-102], [임시:D-성-134], 피그마 62341:1878): 칸이 있으면 390×420 칸을
 * 그리고 누르면 내 러너로 간다. 칸 바닥선(칸 위에서 342dp, 아래에서 78dp)은 지금 아바타 발 자리에 온다. 칸이 비면 지금 그림이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class HomeAvatarSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private val routes = mutableListOf<String>()
    private val state = HomeUiState(
        isLoading = false,
        header = HomeHeader("달리는곰", "골드", 3, 1_250),
        isHeaderLoading = false,
        isAvatarReady = true,
        weeklyDistance = Distance(12_000),
        weeklyGoal = Distance(20_000),
        streakWeeks = 4
    )

    private val probe: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("home-avatar-probe")) }

    private var slots by mutableStateOf<Map<SlotKey, List<@Composable () -> Unit>>>(emptyMap())

    private fun show(
        filled: Map<SlotKey, List<@Composable () -> Unit>>,
        scope: ReleaseScope = ReleaseScope.ALL_OPEN,
        indication: PressRecordingIndication? = null
    ) {
        slots = filled
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides scope, LocalSlots provides slots) {
                RunkkTheme(reduceMotion = true) {
                    val local = indication ?: LocalIndication.current
                    CompositionLocalProvider(LocalIndication provides local) {
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(
                                state,
                                HomeActions(onNavigate = { routes += it }),
                                Modifier.padding(bottom = RUNKK_BOTTOM_BAR_HEIGHT)
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `칸이 있으면 390×420 칸을 그리고 지금 서기 그림은 그리지 않는다`() {
        show(mapOf(SlotKey.HOME_AVATAR to listOf(probe)))

        val slot = compose.onNodeWithTag("home-avatar-probe", useUnmergedTree = true)
        slot.assertWidthIsEqualTo(390.dp)
        val bounds = slot.getUnclippedBoundsInRoot()
        assertEquals(420f, (bounds.bottom - bounds.top).value, 1f)
        compose.onAllNodesWithTag(DEFAULT_AVATAR_TAG + AvatarExpression.STAND.name, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue(it.isEmpty(), "칸이 그리므로 홈의 정적 서기 그림은 없다") }
    }

    @Test
    fun `칸 바닥선은 방 아래 끝보다 10dp 위에 온다(신발이 솟은 홈 탭 원과 조금만 겹친다)`() {
        show(emptyMap())
        val legacy = compose.onNodeWithContentDescription("내 아바타").getUnclippedBoundsInRoot()

        slots = mapOf(SlotKey.HOME_AVATAR to listOf(probe))
        compose.waitForIdle()
        val slot = compose.onNodeWithTag("home-avatar-probe", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // 정적 그림은 방 아래 끝 + 8dp(AVATAR_GROUND_SINK)에 발을 둔다. Rive 칸은 그보다 18dp 위(방 아래 끝 − 10dp)다(2026-11-11 실기기 3차:
        // 같은 자리에 두면 신발 대부분이 솟은 홈 탭 원에 가려졌다).
        val room = legacy.bottom.value - 8f
        assertEquals(
            room - HOME_AVATAR_FLOOR_ABOVE_ROOM.value,
            slot.bottom.value - HOME_AVATAR_FLOOR_FROM_BOTTOM.value,
            1f
        )
    }

    @Test
    fun `칸을 누르면 내 러너로 간다`() {
        show(mapOf(SlotKey.HOME_AVATAR to listOf(probe)))
        compose.onNodeWithContentDescription("내 아바타").assertHasClickAction().performClick()
        assertEquals(listOf(Routes.AVATAR_RUNNER), routes)
    }

    @Test
    fun `칸을 눌러도 물결(누름 표시)을 그리지 않고 단추 안내는 그대로다(D-성-182)`() {
        val indication = PressRecordingIndication()
        show(mapOf(SlotKey.HOME_AVATAR to listOf(probe)), indication = indication)
        assertNoRippleButton(HOME_AVATAR_SLOT_TAG, indication)
        assertEquals(listOf(Routes.AVATAR_RUNNER), routes)
    }

    @Test
    fun `칸이 비어 지금 서기 그림을 그려도 눌렀을 때 물결이 없다(D-성-182)`() {
        val indication = PressRecordingIndication()
        show(emptyMap(), indication = indication)
        val created = indication.created
        compose.onNodeWithContentDescription(text(R.string.home_avatar_description))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickLabel(text(R.string.home_house_edit_avatar)))
            .performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(created, indication.created, "누를 때 표시를 만들지 않는다")
        assertEquals(0, indication.presses)
        assertEquals(listOf(Routes.AVATAR_RUNNER), routes)
    }

    /** 시험 장치 확인: 물결이 있는 단추(머리 줄 프로필)를 누르면 기록된다. */
    @Test
    fun `물결이 있는 단추는 눌림 표시가 기록된다(시험 장치 확인)`() {
        val indication = PressRecordingIndication()
        show(mapOf(SlotKey.HOME_AVATAR to listOf(probe)), indication = indication)
        val created = indication.created
        compose.onNode(hasClickLabel(text(R.string.home_open_profile_edit))).performTouchInput { click(center) }
        compose.waitForIdle()
        assertTrue(indication.created > created || indication.presses > 0)
    }

    private fun assertNoRippleButton(tag: String, indication: PressRecordingIndication) {
        val created = indication.created
        compose.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickLabel(text(R.string.home_house_edit_avatar)))
            .assertHasClickAction()
            .performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(created, indication.created, "누를 때 표시를 만들지 않는다")
        assertEquals(0, indication.presses, "눌림을 표시로 그리지 않는다")
    }

    @Test
    fun `아바타 꾸미기가 닫힌 배포에서는 칸을 눌러도 이동하지 않는다`() {
        show(mapOf(SlotKey.HOME_AVATAR to listOf(probe)), ReleaseScope.FIRST_RELEASE)
        compose.onNodeWithTag(HOME_AVATAR_SLOT_TAG).assertHasNoClickAction()
    }
}

private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

private fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("onClickLabel = $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

/**
 * 눌림 표시(물결 등)를 만드는지 기록하는 표시. 표시를 쓰는 누름 요소마다 [create]가 불리고(누를 때 늦게 만들기도 한다), 붙은 뒤에는
 * 눌림을 센다. `indication = null`이면 이 표시는 쓰이지 않는다.
 */
internal class PressRecordingIndication : IndicationNodeFactory {
    var created = 0
    var presses = 0

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        created++
        return object : Modifier.Node() {
            override fun onAttach() {
                coroutineScope.launch {
                    interactionSource.interactions.collect { if (it is PressInteraction.Press) presses++ }
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}
