package com.a207.runkk.app.data.guide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 도메인 안내 키 → 문자열 리소스(2차 리뷰 M-3). 문구는 도메인이 만들던 것과 같아야 한다. */
@RunWith(RobolectricTestRunner::class)
class GuidanceTextsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun text(message: GuidanceMessage, vararg args: Any) = GuidanceTexts.text(
        Guidance(Guidance.Kind.SPLIT, message, args.toList(), hasVoice = true, hasVibration = false),
        resources
    )

    @Test
    fun `인자를 넣어 도메인이 만들던 문구와 같게 바꾼다`() {
        assertEquals("1km 지점입니다", text(GuidanceMessage.SPLIT, 1L))
        assertEquals("목표 거리 5.0km에 도달했어요", text(GuidanceMessage.TARGET_REACHED, 5.0))
        assertEquals("70m 앞서는 중, 남은 거리 2500m", text(GuidanceMessage.GAP_AHEAD_REMAINING, 70, 2_500L))
        assertEquals("30m 뒤처져 있어요", text(GuidanceMessage.GAP_BEHIND, 30))
        assertEquals("상대를 추월했어요! 12m 앞서는 중", text(GuidanceMessage.OVERTOOK, 12))
    }

    @Test
    fun `인자 없는 문구는 서식 없이 읽는다`() {
        assertEquals("목표 페이스보다 10% 넘게 느려요", text(GuidanceMessage.PACE_SLOW))
    }

    @Test
    fun `모든 안내 키에 문구가 있다`() {
        GuidanceMessage.entries.forEach {
            assertTrue(resources.getString(GuidanceTexts.textRes(it)).isNotBlank(), it.name)
        }
    }
}
