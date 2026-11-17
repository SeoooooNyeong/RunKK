package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.a207.runkk.app.core.designsystem.CELEBRITY_FIGURE_TAG
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.PlacementRoundState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 대결 결과 화면의 아바타는 얼굴 원이 아니라 전신 정지 그림 칸(SlotKey.AVATAR_STILL)이다(S15P31A207-300, PM 2026-11-13).
 * 나·과거의 나는 내 그림, 배틀런 상대는 표시 성별의 기본 그림, 셀럽은 셀럽 전신 그림이다. 다크(밤 거르개)에서도 같다.
 */
@RunWith(RobolectricTestRunner::class)
class BattleResultStillTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `셀프런 결과는 현재 모습 대신 저장된 모습을 슬롯에 보낸다`() {
        val saved = RunnerLook(Gender.FEMALE, 12, 22, 32, 2, 80, "head:old")
        val subjects = mutableListOf<com.a207.runkk.app.core.navigation.AvatarStillSubject>()
        val inspect: @Composable () -> Unit = {
            LocalSlotAvatarStill.current?.let { subjects += it }
            Box(Modifier.fillMaxSize())
        }
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(inspect))) {
                RunkkTheme(reduceMotion = true) {
                    BattleResultScreen(
                        BattleResultUiState(
                            isLoading = false,
                            result = battleResult().copy(
                                type = BattleHistory.Type.SELF,
                                opponentLook = saved
                            )
                        ),
                        {},
                        {},
                        {}
                    )
                }
            }
        }
        compose.runOnIdle {
            kotlin.test.assertTrue(subjects.any { it == pastSelfStill(saved) }, "저장 모습이 상대 슬롯까지 전달된다")
            kotlin.test.assertTrue(subjects.any { it.isMine && it.look == null }, "현재 내 모습은 별도 슬롯이다")
        }
    }

    private val probe: @Composable () -> Unit = {
        val subject = LocalSlotAvatarStill.current
        val tag = when {
            subject == null -> "still-none"
            subject.isMine -> MINE
            subject.isFemale == true -> OTHER_FEMALE
            else -> OTHER_MALE
        }
        Box(Modifier.fillMaxSize().testTag(tag))
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
            RunkkTheme(darkTheme = true, reduceMotion = true) { content() }
        }
    }

    private fun count(tag: String, expected: Int) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(expected)

    @Test
    fun `배틀런 결과 - 나와 상대 모두 전신 정지 그림이고 얼굴 그림이 없다`() {
        show {
            BattleResultScreen(
                BattleResultUiState(isLoading = false, result = battleResult().copy(opponentGender = Gender.FEMALE)),
                {},
                {},
                {}
            )
        }
        count(MINE, 1)
        count(OTHER_FEMALE, 1)
        count(DEFAULT_AVATAR_TAG + "JOY", 0)
    }

    @Test
    fun `셀프런 결과 - 과거의 나도 내 전신 정지 그림이다`() {
        show {
            BattleResultScreen(
                BattleResultUiState(
                    isLoading = false,
                    result = battleResult().copy(type = BattleHistory.Type.SELF, opponentName = "10월 2일의 나")
                ),
                {},
                {},
                {}
            )
        }
        count(MINE, 2)
        count(DEFAULT_AVATAR_TAG + "STAND", 0)
    }

    @Test
    fun `셀럽런 결과 - 나는 전신 정지 그림, 셀럽은 셀럽 전신 그림이다`() {
        show {
            BattleResultScreen(
                BattleResultUiState(
                    isLoading = false,
                    result = battleResult(verdict = "LOSE").copy(type = BattleHistory.Type.CELEBRITY)
                ),
                {},
                {},
                {}
            )
        }
        count(MINE, 1)
        count(CELEBRITY_FIGURE_TAG, 1)
        count(DEFAULT_AVATAR_TAG + "SAD", 0)
    }

    @Test
    fun `대결 결과 상세 쪽 - 거리 옆 아바타도 내 전신 정지 그림이다`() {
        show {
            BattleResultScreen(
                BattleResultUiState(isLoading = false, result = battleResult(), detail = recordDetail()),
                {},
                {},
                {},
                pagerState = rememberPagerState(initialPage = 1) { 2 }
            )
        }
        count(MINE, 1)
    }

    @Test
    fun `배치고사 판 결과 - 거리 히어로 아바타는 내 전신 정지 그림이다`() {
        show {
            PlacementResultScreen(
                PlacementResultUiState(
                    isLoading = false,
                    battleId = 9,
                    result = battleResult().copy(placementRound = 1, placementState = PlacementRoundState.COUNTED),
                    detail = recordDetail(),
                    state = PlacementRoundState.COUNTED,
                    progress = placing(1, "GOLD")
                ),
                PlacementResultActions()
            )
        }
        count(MINE, 1)
    }

    @Test
    fun `배치고사 완료 - 축하 아바타는 내 전신 정지 그림이다`() {
        show {
            PlacementCompleteScreen(
                PlacementCompleteUiState(
                    isLoading = false,
                    tier = "GOLD",
                    rounds = listOf(placementRound(1, 901, "WIN")),
                    wins = 1,
                    losses = 0
                ),
                PlacementCompleteActions()
            )
        }
        count(MINE, 1)
        count(DEFAULT_AVATAR_TAG + "JOY", 0)
    }

    private companion object {
        const val MINE = "still-mine"
        const val OTHER_FEMALE = "still-other-female"
        const val OTHER_MALE = "still-other-male"
    }
}
