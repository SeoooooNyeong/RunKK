package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 전신 정지 그림 준비 신호([임시:D-성-292]): 그림이 보이면 `onReady`를 한 번 부른다. 기록 카드 공유 단계가 이 신호 전에는 공유 단추를 막는다
 * (그림을 읽는 동안 칸이 비어 있어 빈 아바타 이미지가 나가지 않게).
 */
@RunWith(RobolectricTestRunner::class)
class RiveAvatarStillReadyTest {
    @get:Rule
    val compose = createComposeRule()

    private val look = RiveAvatarLook(AvatarGender.MALE, AvatarOutfit(), AvatarBodyShape.BASE, 37, null)

    @Test
    fun `새 모습과 새 크기의 비트맵 준비 중에는 대체 그림이 남고 준비되면 사라진다`() {
        var current by mutableStateOf(look.copy(runningAmount = 94))
        var width by mutableStateOf(63.dp)
        var cached by mutableStateOf(false)
        var readies = 0
        compose.setContent {
            if (cached) {
                val size = with(LocalDensity.current) { width.roundToPx() to 103.dp.roundToPx() }
                AvatarStillCache.put(
                    AvatarStillKey.of(current, size.first, size.second),
                    Bitmap.createBitmap(30, 50, Bitmap.Config.ARGB_8888)
                )
            }
            RiveAvatarStill(
                current,
                Modifier.size(width, 103.dp),
                onReady = { readies++ },
                pending = { Box(Modifier.fillMaxSize().testTag("pending-fallback")) }
            )
        }
        compose.onNodeWithTag("pending-fallback").assertExists()
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertDoesNotExist()
        assertEquals(0, readies)

        compose.runOnIdle {
            cached = true
            width = 64.dp
        }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertExists()
        compose.onNodeWithTag("pending-fallback").assertDoesNotExist()
        assertEquals(1, readies)

        compose.runOnIdle {
            cached = false
            current = look.copy(runningAmount = 95)
        }
        compose.onNodeWithTag("pending-fallback").assertExists()
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertDoesNotExist()
        compose.runOnIdle { width = 65.dp }
        compose.onNodeWithTag("pending-fallback").assertExists()
        assertEquals(1, readies)

        compose.runOnIdle {
            cached = true
            width = 66.dp
        }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertExists()
        compose.onNodeWithTag("pending-fallback").assertDoesNotExist()
        assertEquals(2, readies)
    }

    @Test
    fun `onReady가 그림이 보이면 한 번 불린다`() {
        var readies = 0
        var tick by mutableIntStateOf(0)
        compose.setContent {
            // 읽어 둔 그림이 캐시에 있는 칸(같은 모습, 같은 칸 크기)
            val size = with(LocalDensity.current) { 60.dp.roundToPx() to 100.dp.roundToPx() }
            AvatarStillCache.put(
                AvatarStillKey.of(look, size.first, size.second),
                Bitmap.createBitmap(30, 50, Bitmap.Config.ARGB_8888)
            )
            // 다시 그려도(상태 변화) 다시 부르지 않는다
            tick.let { RiveAvatarStill(look, Modifier.size(60.dp, 100.dp), onReady = { readies++ }) }
        }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertExists()
        assertEquals(1, readies)
        tick++
        compose.waitForIdle()
        assertEquals(1, readies)
    }

    @Test
    fun `같은 모습을 더 큰 칸에 읽어 두었으면 작은 칸도 Rive 없이 바로 그린다`() {
        // 기록런 VS 칸(148)에 읽어 둔 그림을 카운트다운 VS 칸(96)이 바로 쓴다(S15P31A207-335 8회차)
        val shown = look.copy(runningAmount = 61)
        var readies = 0
        compose.setContent {
            val big = with(LocalDensity.current) { 148.dp.roundToPx() }
            AvatarStillCache.put(
                AvatarStillKey.of(shown, big, big),
                Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
            )
            RiveAvatarStill(shown, Modifier.size(96.dp), onReady = { readies++ })
        }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertExists()
        assertEquals(1, readies)
    }

    @Test
    fun `넓은 칸 그림은 좁은 칸에 쓰지 않고 작은 칸 그림은 큰 칸에 늘려 쓰지 않는다`() {
        val wide = look.copy(runningAmount = 62)
        val small = look.copy(runningAmount = 63)
        compose.setContent {
            val px = with(LocalDensity.current) { 1.dp.roundToPx() }
            // 정사각 칸 그림(몸 둘레가 넓다)을 좁고 긴 칸(70×130)에 쓰면 몸이 작아진다
            AvatarStillCache.put(
                AvatarStillKey.of(wide, 200 * px, 200 * px),
                Bitmap.createBitmap(200 * px, 200 * px, Bitmap.Config.ARGB_8888)
            )
            // 작은 칸 그림을 큰 칸에 늘리면 흐려진다
            AvatarStillCache.put(
                AvatarStillKey.of(small, 40 * px, 40 * px),
                Bitmap.createBitmap(40 * px, 40 * px, Bitmap.Config.ARGB_8888)
            )
            Box {
                Box(Modifier.testTag("narrow")) { RiveAvatarStill(wide, Modifier.size(70.dp, 130.dp)) }
                Box(Modifier.testTag("big")) { RiveAvatarStill(small, Modifier.size(96.dp)) }
            }
        }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertDoesNotExist()
    }

    @Test
    fun `그림을 읽기 전에는 onReady를 부르지 않는다`() {
        var readies = 0
        val other = look.copy(runningAmount = 91)
        compose.setContent { RiveAvatarStill(other, Modifier.size(61.dp, 101.dp), onReady = { readies++ }) }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertDoesNotExist()
        assertEquals(0, readies)
    }
}
