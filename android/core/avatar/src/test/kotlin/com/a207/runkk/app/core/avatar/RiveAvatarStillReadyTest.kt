package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
    fun `그림을 읽기 전에는 onReady를 부르지 않는다`() {
        var readies = 0
        val other = look.copy(runningAmount = 91)
        compose.setContent { RiveAvatarStill(other, Modifier.size(61.dp, 101.dp), onReady = { readies++ }) }
        compose.onNodeWithTag(AVATAR_STILL_TAG).assertDoesNotExist()
        assertEquals(0, readies)
    }
}
