package com.a207.runkk.app.ui.record

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillOnReady
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.ui.share.CapturedCard
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 공유 그림은 화면의 앞면과 같다([임시:FR-성-34], [임시:D-성-291], [임시:D-성-292]): 공유 단계는 화면에 그린 앞면을 그대로 찍어 넘기고,
 * 전신 아바타가 보이기 전(정지 그림을 읽는 동안 칸이 비어 있다)에는 공유 단추를 막는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RecordCardShareEqualsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val rendered = Bitmap.createBitmap(8, 10, Bitmap.Config.ARGB_8888)
    private val shared = mutableListOf<Pair<CardShareKind, CapturedCard?>>()

    private fun show(slot: (@Composable () -> Unit)? = null) = compose.setContent {
        CompositionLocalProvider(
            LocalSlots provides (slot?.let { mapOf(SlotKey.AVATAR_STILL to listOf(it)) } ?: emptyMap())
        ) {
            RunkkTheme(darkTheme = false, reduceMotion = true) {
                RecordCardScreen(
                    RecordCardUiState(
                        isLoading = false,
                        card = runCard(),
                        front = rendered,
                        back = rendered,
                        isShareStep = true
                    ),
                    onClose = {},
                    onSideSelected = {},
                    onShare = {},
                    onMessageShown = {},
                    onRetry = {},
                    onShareCaptured = { kind, captured -> shared += kind to captured }
                )
            }
        }
    }

    @Test
    fun `정지 그림이 준비되기 전에는 공유 단추가 막힌다`() {
        var signal: (() -> Unit)? = null
        // 그림을 읽는 중인 칸: 준비 신호를 아직 보내지 않는다.
        show(slot = {
            signal = LocalSlotAvatarStillOnReady.current
            Box(Modifier.fillMaxSize())
        })
        compose.onRoot().captureToImage()
        compose.onNodeWithText("더 보기").performClick()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), shared.map { it.first }, "아바타가 보이기 전에는 찍지 않는다")

        compose.runOnIdle { assertNotNull(signal).invoke() }
        compose.onRoot().captureToImage()
        compose.onNodeWithText("더 보기").performClick()
        compose.waitForIdle()
        assertEquals(listOf(CardShareKind.SYSTEM), shared.map { it.first })
    }

    @Test
    fun `캡처 이미지는 화면의 앞면과 같은 크기이고 픽셀이 같다`() {
        show()
        val onScreen = compose.onNodeWithContentDescription("러닝 카드 앞면", substring = true)
            .captureToImage().asAndroidBitmap()
        compose.onNodeWithText("더 보기").performClick()
        compose.waitForIdle()
        val captured = assertNotNull(shared.single().second, "화면 경로는 찍은 이미지를 넘긴다").bitmap
        assertEquals(onScreen.width to onScreen.height, captured.width to captured.height)
        // 카드 안 여러 점(그림 칸의 하늘·아바타·트랙, 글자 줄 바탕)의 색이 같다.
        val points = listOf(0.5f to 0.1f, 0.5f to 0.3f, 0.5f to 0.45f, 0.3f to 0.6f, 0.5f to 0.75f, 0.2f to 0.92f)
        points.forEach { (fx, fy) ->
            val x = (onScreen.width * fx).toInt()
            val y = (onScreen.height * fy).toInt()
            assertSameColor(onScreen.getPixel(x, y), captured.getPixel(x, y), "($fx, $fy)")
        }
    }

    private fun assertSameColor(expected: Int, actual: Int, where: String) {
        val difference = listOf(16, 8, 0).maxOf { abs((expected shr it and 0xFF) - (actual shr it and 0xFF)) }
        assertTrue(difference <= 2, "$where: ${Integer.toHexString(expected)} != ${Integer.toHexString(actual)}")
    }
}
