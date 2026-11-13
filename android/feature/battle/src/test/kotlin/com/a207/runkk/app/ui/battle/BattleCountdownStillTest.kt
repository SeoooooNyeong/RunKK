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
