package com.a207.runkk.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 전신 정지 그림 칸(SlotKey.AVATAR_STILL, S15P31A207-300): 랭킹 시상대·기록런 VS 칸은 누구를 그릴지만 넘기고 칸 크기를 정한다.
 * 칸이 비면 지금 그림을 그리고, 칸 내용에는 지금 그림을 넘긴다(꾸미기가 닫혀 있으면 칸 내용이 그것을 그린다).
 */
@RunWith(RobolectricTestRunner::class)
class AvatarStillSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private var seen: AvatarStillSubject? = null

    private val probe: @Composable () -> Unit = {
        seen = LocalSlotAvatarStill.current
        Box(Modifier.fillMaxSize().testTag("slot"))
    }

    @Test
    fun `칸이 비면 지금 그림을 그린다`() {
        compose.setContent {
            AvatarStillSlot(AvatarStillSubject(isMine = true)) { Box(Modifier.testTag("fallback")) }
        }
        compose.onNodeWithTag("fallback").assertExists()
        compose.onNodeWithTag("slot").assertDoesNotExist()
    }

    @Test
    fun `칸이 있으면 사람을 넘기고 부르는 쪽 크기 칸을 채운다`() {
        val subject = AvatarStillSubject(isMine = false, isFemale = true)
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
                AvatarStillSlot(subject, Modifier.size(60.dp, 100.dp)) { Box(Modifier.testTag("fallback")) }
            }
        }
        compose.onNodeWithTag("slot").assertWidthIsEqualTo(60.dp).assertHeightIsEqualTo(100.dp)
        compose.onNodeWithTag("fallback").assertDoesNotExist()
        assertEquals(subject, seen)
    }

    @Test
    fun `받은 모습이 있으면 그 모습을 칸에 넘긴다(D-성-272)`() {
        val look = SlotRunnerLook(isFemale = false, topItemId = 12, faceKey = "head:7:ab12")
        val subject = AvatarStillSubject(isMine = false, isFemale = false, slotLook = look)
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
                AvatarStillSlot(subject, Modifier.size(60.dp, 100.dp)) { Box(Modifier.testTag("fallback")) }
            }
        }
        assertEquals(look, seen?.slotLook)
        assertEquals(null, AvatarStillSubject.MINE.slotLook)
    }

    @Test
    fun `칸 내용에 부르는 쪽 지금 그림을 넘긴다(꾸미기 공개 여부는 칸 내용이 본다)`() {
        val drawsFallback: @Composable () -> Unit = { LocalSlotAvatarStillFallback.current?.invoke() }
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(drawsFallback))) {
                AvatarStillSlot(AvatarStillSubject(isMine = true)) { Box(Modifier.testTag("fallback")) }
            }
        }
        compose.onNodeWithTag("fallback").assertExists()
    }

    @Test
    fun `MINE은 isMine이다`() {
        assertEquals(AvatarStillSubject(isMine = true), AvatarStillSubject.MINE)
    }

    @Test
    fun `대체 경로(칸이 빔)에서도 onReady가 한 번 불린다`() {
        var readies = 0
        compose.setContent {
            AvatarStillSlot(AvatarStillSubject.MINE, onReady = { readies++ }) { Box(Modifier.testTag("fallback")) }
        }
        compose.onNodeWithTag("fallback").assertExists()
        assertEquals(1, readies)
    }

    @Test
    fun `칸이 있으면 onReady를 칸 내용에 넘기고 스스로 부르지 않는다`() {
        var readies = 0
        var signal: (() -> Unit)? = null
        val content: @Composable () -> Unit = { signal = LocalSlotAvatarStillOnReady.current }
        compose.setContent {
            CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(content))) {
                AvatarStillSlot(AvatarStillSubject.MINE, onReady = { readies++ }) { Box(Modifier.testTag("fallback")) }
            }
        }
        compose.waitForIdle()
        assertEquals(0, readies, "그림이 보일 때 칸 내용이 부른다")
        signal?.invoke()
        assertEquals(1, readies)
    }
}
