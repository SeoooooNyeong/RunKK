package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 달리기 화면 위에 겹쳐 뜨는 안내 띠(추월·역전·종료 카운트다운, S15P31A207-334). */
@RunWith(RobolectricTestRunner::class)
class RunkkGuidanceToastTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `안내 띠 바탕은 라이트·다크 모두 흰색이고 글자는 바로 읽힌다`() {
        var isDark by mutableStateOf(false)
        compose.setContent {
            RunkkTheme(darkTheme = isDark, reduceMotion = true) {
                RunkkGuidanceToast("역전당했어요", tone = RunkkGuidanceTone.Rival)
            }
        }
        listOf(false, true).forEach { dark ->
            isDark = dark
            compose.waitForIdle()
            val image = compose.onNodeWithTag(RUNKK_GUIDANCE_TOAST_TAG, useUnmergedTree = true)
                .captureToImage().asAndroidBitmap()
            val edge = with(compose.density) { 8.dp.roundToPx() }
            assertEquals(android.graphics.Color.WHITE, image.getPixel(image.width - edge, image.height / 2), "다크 $dark")
            compose.onNodeWithText("역전당했어요")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }
    }

    @Test
    fun `읽기 문구를 주면 바뀌는 초 대신 고정 문구를 즉시 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkGuidanceToast(
                    "상대 도착! 27초 안에 도착하지 못하면 대결이 끝나요",
                    tone = RunkkGuidanceTone.Closing,
                    spoken = "상대 도착. 곧 대결이 끝나요"
                )
            }
        }
        compose.onNodeWithContentDescription("상대 도착. 곧 대결이 끝나요")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun `겹침 층은 안내가 있을 때만 띠를 그리고 움직임 줄이기면 바로 사라진다`() {
        var notice by mutableStateOf<String?>(null)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(200.dp, 40.dp)) {
                    RunkkGuidanceOverlay(notice, Modifier.size(200.dp, 40.dp)) {
                        RunkkGuidanceToast(it, tone = RunkkGuidanceTone.Lead)
                    }
                }
            }
        }
        compose.onNodeWithTag(RUNKK_GUIDANCE_TOAST_TAG, useUnmergedTree = true).assertDoesNotExist()
        notice = "추월했어요!"
        compose.waitForIdle()
        compose.onNodeWithText("추월했어요!").assertExists()
        notice = null
        compose.waitForIdle()
        compose.onNodeWithTag(RUNKK_GUIDANCE_TOAST_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
