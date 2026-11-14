package com.a207.runkk.app.ui.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RESULT_STAGE_DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.record.CardWeather
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 기록 카드 앞면의 그림 칸([임시:FR-성-34], [임시:D-성-289], [임시:D-성-290]): 그림 칸은 카드 높이의 0.60(±0.005)이고 그 안에 내 전신
 * 아바타가 선다. 그림 칸이 커져도 날짜·거리·통계 줄이 잘리지 않는다(카드 너비가 달라도 같다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RecordCardFrontRatioTest {
    @get:Rule
    val compose = createComposeRule()

    private val card = runCard().copy(
        newRecords = listOf("LONGEST_DISTANCE"),
        averageHeartRate = 152,
        weather = CardWeather(14, CardWeather.Sky.CLEAR)
    )

    private var seen: AvatarStillSubject? = null

    /** 전신 정지 그림 칸을 채운 기능 흉내. */
    private val still: @Composable () -> Unit = {
        seen = LocalSlotAvatarStill.current
        Box(Modifier.fillMaxSize().testTag("still"))
    }

    private var width by mutableStateOf(334.dp)

    private fun show(slot: (@Composable () -> Unit)? = null) = compose.setContent {
        CompositionLocalProvider(
            LocalSlots provides (slot?.let { mapOf(SlotKey.AVATAR_STILL to listOf(it)) } ?: emptyMap())
        ) {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.width(width)) { RecordCardFront(card, Modifier.testTag("front")) }
            }
        }
    }

    /**
     * 뿌리 좌표의 경계(px). 앞면은 카드 너비에 맞춘 밀도로 그리므로(dp가 카드와 같이 늘고 준다) dp 경계는 요소마다 단위가 달라 px로 잰다.
     */
    private fun boundsOf(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun boundsOfText(text: String): Rect =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `그림 칸 높이는 카드 높이의 0·60±0·005다(카드 너비 334, 304, 280)`() {
        show()
        listOf(334.dp, 304.dp, 280.dp).forEach {
            width = it
            compose.waitForIdle()
            val share = boundsOf(RECORD_CARD_PICTURE_TAG).height / boundsOf("front").height
            assertTrue(abs(share - RECORD_CARD_PICTURE_SHARE) <= 0.005f, "너비 $it: 그림 칸 비율 $share")
        }
    }

    @Test
    fun `카드는 334×383이고 내 전신 아바타 칸은 그림 칸 높이의 0·80으로 트랙 가운데에 선다`() {
        show(still)
        val front = boundsOf("front")
        assertEquals(383f / 334f, front.height / front.width, 0.005f)
        assertEquals(AvatarStillSubject.MINE, seen)
        val picture = boundsOf(RECORD_CARD_PICTURE_TAG)
        val avatar = boundsOf("still")
        assertEquals(0.80f, avatar.height / picture.height, 0.01f)
        // 발끝은 그림 칸 바닥에서 그려진 그림 높이(칸 너비 ÷ 0.9)의 9% 위다.
        assertEquals(0.09f * picture.width / 0.9f, picture.bottom - avatar.bottom, 2f)
        assertEquals(picture.center.x, avatar.center.x, 1f)
    }

    @Test
    fun `전신 정지 그림 칸이 비면 기본 서기 그림이 선다`() {
        show()
        compose.onNodeWithTag(RESULT_STAGE_DEFAULT_AVATAR_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `날짜·거리·통계 줄이 잘리지 않는다(카드 너비 334, 304)`() {
        show()
        listOf(334.dp, 304.dp).forEach {
            width = it
            compose.waitForIdle()
            val front = boundsOf("front")
            // 시안 1dp가 몇 px인가(카드 너비 334 기준)
            val unit = front.width / 334f
            // 앞면 아래 안쪽 여백은 14다.
            val contentBottom = front.bottom - 14f * unit
            val label = boundsOfText("평균 심박")
            val value = boundsOfText("152")
            val date = boundsOfText("맑음 14°")
            val distance = boundsOf(RECORD_CARD_DISTANCE_TAG)
            val rows = "unit=$unit date=${date.height / unit} value=${value.height / unit} " +
                "label=${label.height / unit} distance=${distance.height / unit} " +
                "spare=${(contentBottom - label.bottom) / unit}"
            assertTrue(label.bottom <= contentBottom + 0.5f * unit, "너비 $it: 통계 줄이 넘친다 $rows")
            // 같은 글자 모양(캡션)인 날짜 줄과 통계 이름 줄의 높이가 같다(눌리지 않았다).
            assertEquals(date.height, label.height, unit, "너비 $it: 통계 이름 줄이 눌렸다 $rows")
            // 큰 거리 글자(시안 40)는 글자 크기보다 낮아지지 않는다.
            assertTrue(distance.height >= 40f * unit, "너비 $it: 거리 줄이 눌렸다 $rows")
            assertTrue(value.height >= 16f * unit, "너비 $it: 통계 값 줄이 눌렸다 $rows")
        }
    }

    @Test
    fun `글자 내용은 그대로다(날짜, 날씨, 거리, 신기록, 세 지표)`() {
        show()
        compose.onNodeWithText("맑음 14°", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("5.02", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("신기록", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("운동 시간", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 페이스", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("152", useUnmergedTree = true).assertExists()
    }
}
