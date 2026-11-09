package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.Pace
import kotlin.test.Test
import kotlin.test.assertEquals

/** 피그마 런바디 표기(S15P31A207-294): 페이스는 분·초 기호(′ ″), 활동 시간은 시:분:초 두 자리. */
class RunBodyFormatsTest {
    @Test
    fun `페이스는 피그마처럼 프라임 기호로 적는다`() {
        assertEquals("5′48″", runBodyPace(Pace(348)))
        assertEquals("6′02″", runBodyPace(Pace(362)))
        assertEquals("-′--″", runBodyPace(null))
    }
}
