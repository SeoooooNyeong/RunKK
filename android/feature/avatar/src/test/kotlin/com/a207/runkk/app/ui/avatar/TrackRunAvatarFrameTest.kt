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
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarLoading
import com.a207.runkk.app.core.designsystem.RunkkTheme
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
