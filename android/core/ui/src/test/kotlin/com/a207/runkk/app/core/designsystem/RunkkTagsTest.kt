package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RunkkTagsTest {
    @Test
    fun `고정 목록은 평일과 주말 두 줄에 시간대 넷씩 8개다`() {
        assertEquals(listOf(4, 4), RunkkTags.categories.map { it.tags.size })
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals("평일 저녁", RunkkTags.label(res, "WEEKDAY_EVENING"))
        assertEquals("아침", res.getString(RunkkTags.of("WEEKEND_MORNING")!!.slotRes))
        assertEquals(true, RunkkTags.of("WEEKEND_MORNING")!!.isWeekend)
    }

    @Test
    fun `켜진 태그를 다시 누르면 꺼진다`() {
        assertEquals(emptySet(), RunkkTags.toggle(setOf("WEEKDAY_EVENING"), "WEEKDAY_EVENING"))
    }

    @Test
    fun `같은 줄의 태그도 함께 고를 수 있고 3개를 넘기면 바꾸지 않는다`() {
        val two = RunkkTags.toggle(setOf("WEEKDAY_EVENING"), "WEEKDAY_DAWN")
        assertEquals(setOf("WEEKDAY_EVENING", "WEEKDAY_DAWN"), two)
        val three = RunkkTags.toggle(two, "WEEKEND_MORNING")
        assertEquals(three, RunkkTags.toggle(three, "WEEKEND_LUNCH"))
    }

    @Test
    fun `모르는 태그는 켜지 않고 목록 순서로 늘어놓는다`() {
        assertEquals(emptySet(), RunkkTags.toggle(emptySet(), "SOCIAL_RUN"))
        assertEquals(
            listOf("WEEKDAY_EVENING", "WEEKEND_MORNING", "SOCIAL_RUN"),
            RunkkTags.sorted(setOf("SOCIAL_RUN", "WEEKEND_MORNING", "WEEKDAY_EVENING"))
        )
    }
}
