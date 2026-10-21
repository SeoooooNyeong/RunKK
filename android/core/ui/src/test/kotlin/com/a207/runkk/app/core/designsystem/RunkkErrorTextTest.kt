package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class RunkkErrorTextTest {
    @Test
    fun `알려진 사유 코드는 SRS 문구로, 모르는 코드는 일반 문구로`() {
        assertEquals("만 14세 이상만 가입할 수 있음", RunkkErrorText.of(AppError.Rejected("UNDER_14")))
        assertEquals("요청을 처리하지 못했어요", RunkkErrorText.of(AppError.Rejected("SOMETHING_NEW")))
    }

    @Test
    fun `네트워크·인증·저장 오류는 각자 문구`() {
        assertEquals(true, RunkkErrorText.of(AppError.Network).startsWith("네트워크"))
        assertEquals(true, RunkkErrorText.of(AppError.Unauthorized).contains("로그인"))
        assertEquals(true, RunkkErrorText.of(AppError.Storage).contains("저장"))
    }
}
