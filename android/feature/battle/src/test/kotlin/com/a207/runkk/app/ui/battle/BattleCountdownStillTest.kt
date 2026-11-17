package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import java.time.Duration
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 셀프런 카운트다운(D-2)의 과거의 나는 기본 서기 그림이 아니라 반투명한 내 전신 정지 그림 칸(SlotKey.AVATAR_STILL)이다
 * (S15P31A207-300, 결과 화면과 같은 PastSelfStill). 나도 내 전신 정지 그림이다.
 */
@RunWith(RobolectricTestRunner::class)
class BattleCountdownStillTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `과거의 나 대결자 카드는 받은 모습을 끝까지 전달한다`() {
        val saved = RunnerLook(Gender.MALE, 11, 21, 31, 1, 70, "head:old")
        var seen: com.a207.runkk.app.core.navigation.AvatarStillSubject? = null
        compose.setContent {
            val inspect: @Composable () -> Unit = {
                seen = LocalSlotAvatarStill.current
                Box(Modifier.fillMaxSize())
            }
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(inspect))) {
                RunkkTheme(reduceMotion = true) {
                    Contender("그날의 나", figure = Figure.PAST_SELF, look = saved)
                }
            }
        }
        compose.runOnIdle { kotlin.test.assertEquals(pastSelfStill(saved), seen) }
    }

    private val probe: @Composable () -> Unit = {
        val subject = LocalSlotAvatarStill.current
        val tag = if (subject?.isMine == true) MINE else "still-other"
        Box(Modifier.fillMaxSize().testTag(tag))
    }

    private fun count(tag: String, expected: Int) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(expected)

    @Test
    fun `셀프런 카운트다운 - 나와 과거의 나 모두 내 전신 정지 그림이고 기본 서기 그림이 없다`() {
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
                RunkkTheme(reduceMotion = true) {
                    BattleRunningScreen(
                        BattleRunningUiState(
                            kind = OpponentKind.SELF,
                            phase = BattlePhase.COUNTDOWN,
                            countdown = 2,
                            opponentRecord = Duration.ofSeconds(1_812),
                            opponentRecordedOn = LocalDate.of(2026, 10, 2)
                        ),
                        BattleRunningActions()
                    )
                }
            }
        }
        count(MINE, 2)
        count(DEFAULT_AVATAR_TAG + "STAND", 0)
    }

    private companion object {
        const val MINE = "still-mine"
    }
}
