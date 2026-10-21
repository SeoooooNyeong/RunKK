package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class RunkkTagsTest {
    @Test
    fun `고정 목록은 7분류 19개이고 러너는 6분류`() {
        assertEquals(19, RunkkTags.categories.sumOf { it.tags.size })
        assertEquals(6, RunkkTags.runnerCategories.size)
        assertEquals("함께가 좋아요", RunkkTags.label("SOCIAL_RUN"))
    }

    @Test
    fun `같은 분류에서 다른 태그를 켜면 앞 태그가 꺼진다`() {
        val selected = RunkkTags.toggle(setOf("SOLO_RUN"), "SOCIAL_RUN")
        assertEquals(setOf("SOCIAL_RUN"), selected)
    }

    @Test
    fun `켜진 태그를 다시 누르면 꺼진다`() {
        assertEquals(emptySet(), RunkkTags.toggle(setOf("SOLO_RUN"), "SOLO_RUN"))
    }

    @Test
    fun `5개를 넘기면 바꾸지 않는다`() {
        val five = setOf("SOLO_RUN", "SHORT_DISTANCE", "FAMILIAR_ROUTE", "ROUTINE_RUN", "DAWN")
        assertEquals(five, RunkkTags.toggle(five, "PERSONAL_BEST"))
        // 같은 분류 안 바꾸기는 상한과 관계없이 된다.
        assertEquals(five - "DAWN" + "NIGHT", RunkkTags.toggle(five, "NIGHT"))
    }
}
