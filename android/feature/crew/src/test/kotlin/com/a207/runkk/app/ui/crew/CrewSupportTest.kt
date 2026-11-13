package com.a207.runkk.app.ui.crew

import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 문자열 리소스를 읽어야 하는 크루 보조 함수 시험. */
@RunWith(RobolectricTestRunner::class)
class CrewSupportTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `모집글 요약은 서버 crewRun의 거리·모집 일시·참여 인원이고 끝난 크루런은 함께 달린 인원을 더한다(FR-133)`() {
        assertEquals("5km, 10월 24일(토) 07:00, 참석 2명", crewRunSummary(resources, crewRunVoting, seoul))
        assertEquals(
            "10km, 10월 24일(토) 07:00, 참석 3명, 3명 참여",
            crewRunSummary(resources, crewRunPosts.posts[1].crewRun!!, seoul)
        )
    }
}
