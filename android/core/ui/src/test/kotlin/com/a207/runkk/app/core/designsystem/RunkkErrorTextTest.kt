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
    fun `서버 응답 오류는 네트워크 문구가 아니고, 찾을 수 없음·잘못된 요청은 사유 문구`() {
        assertEquals(false, RunkkErrorText.of(AppError.Server(500)).startsWith("네트워크"))
        assertEquals(true, RunkkErrorText.of(AppError.Server(503)).contains("잠시 뒤"))
        assertEquals(true, RunkkErrorText.of(AppError.Rejected("NOT_FOUND")).contains("찾을 수 없"))
        assertEquals(true, RunkkErrorText.of(AppError.Rejected("BAD_REQUEST")).contains("요청 내용"))
        assertEquals("만 14세 이상만 가입할 수 있음", RunkkErrorText.of(AppError.Rejected("UNDER_14")))
    }

    @Test
    fun `네트워크·인증·저장 오류는 각자 문구`() {
        assertEquals(true, RunkkErrorText.of(AppError.Network).startsWith("네트워크"))
        assertEquals(true, RunkkErrorText.of(AppError.Unauthorized).contains("로그인"))
        assertEquals(true, RunkkErrorText.of(AppError.Storage).contains("저장"))
    }
}
