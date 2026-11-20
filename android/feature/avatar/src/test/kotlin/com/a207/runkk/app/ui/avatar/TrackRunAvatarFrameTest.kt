package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarLoading
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarWarmup
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackRunAvatarFrameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `트랙 상태가 준비 중에서 실패와 재시도로 바뀌면 기본 그림과 준비 신호도 바뀐다`() {
        var state: RunFigureState by mutableStateOf(RunFigureState.Loading)
        val reports = mutableListOf<Boolean>()
        var errors = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalTrackAvatarLoading provides { reports += it }) {
                    Box(Modifier.size(88.dp)) {
                        TrackRunAvatarFigure(state, AvatarSlotPose.RUN, facingLeft = true) { errors++ }
                    }
                }
            }
        }
        compose.onAllNodesWithTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG).assertCountEquals(0)
        compose.runOnIdle { assertEquals(true, reports.last()) }
        compose.runOnIdle { state = RunFigureState.Failed(AvatarCharacter.FEMALE) }
        compose.onNodeWithTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG).assertExists()
        compose.runOnIdle { assertEquals(false, reports.last()) }
        compose.runOnIdle { state = RunFigureState.Loading }
        compose.onAllNodesWithTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(true, reports.last())
            assertEquals(0, errors)
        }
    }

    @Test
    fun `준비 중에는 기본 그림 없이 카드에 준비 중을 알리고 첫 프레임 뒤에 푼다`() {
        var ready: () -> Unit = {}
        var key by mutableStateOf("first")
        val reports = mutableListOf<Boolean>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalTrackAvatarLoading provides { reports += it }) {
                    Box(Modifier.size(88.dp)) {
                        TrackRunAvatarFrame(key, isFailed = false, character = AvatarCharacter.FEMALE) { ready = it }
                    }
                }
            }
        }
        compose.onAllNodesWithTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG).assertCountEquals(0)
        compose.runOnIdle { assertEquals(true, reports.last()) }
        compose.runOnIdle { ready() }
        compose.runOnIdle { assertEquals(false, reports.last()) }
        compose.runOnIdle { key = "second" }
        compose.runOnIdle { assertEquals(true, reports.last()) }
    }

    @Test
    fun `출발 전 데우기에서는 Rive 칸을 만들지 않고 그 모습의 파일과 텍스처만 미리 준비한다`() {
        val held = mutableListOf<RiveAvatarLook>()
        var errors = 0
        val look = defaultRiveLook(isFemale = false)
        compose.setContent {
            RunkkTheme(reduceMotion = false) {
                CompositionLocalProvider(
                    LocalTrackAvatarWarmup provides true,
                    LocalTrackAvatarHold provides {
                        held += it
                        awaitCancellation()
                    }
                ) {
                    Box(Modifier.size(88.dp)) {
                        TrackRunAvatarFigure(RunFigureState.Ready(look), AvatarSlotPose.RUN, facingLeft = false) {
                            errors++
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(listOf(look), held)
            // Rive 칸을 만들면 Robolectric에서 네이티브 런타임을 읽지 못해 onError가 온다. 오지 않으면 만들지 않은 것이다.
            assertEquals(0, errors)
        }
    }

    @Test
    fun `실패하면 성별 기본 정지 그림을 보이고 준비 중이 아니다`() {
        val reports = mutableListOf<Boolean>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalTrackAvatarLoading provides { reports += it }) {
                    Box(Modifier.size(88.dp)) {
                        TrackRunAvatarFrame("key", isFailed = true, character = AvatarCharacter.FEMALE) {}
                    }
                }
            }
        }
        compose.onNodeWithTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG).assertExists()
        compose.runOnIdle { assertEquals(false, reports.last()) }
    }
}
