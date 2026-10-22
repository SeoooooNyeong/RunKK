package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RunkkErrorTextTest {
    private fun text(error: AppError) = RunkkErrorText.of(error).asString(RuntimeEnvironment.getApplication())

    private fun rejectedText(code: String) = RunkkErrorText.rejected(code).asString(RuntimeEnvironment.getApplication())

    @Test
    fun `알려진 사유 코드는 SRS 문구로, 모르는 코드는 일반 문구로`() {
        assertEquals("만 14세 이상만 가입할 수 있음", text(AppError.Rejected("UNDER_14")))
        assertEquals("요청을 처리하지 못했어요", text(AppError.Rejected("SOMETHING_NEW")))
    }

    @Test
    fun `서버 응답 오류는 네트워크 문구가 아니고, 찾을 수 없음·잘못된 요청은 사유 문구`() {
        assertEquals(false, text(AppError.Server(500)).startsWith("네트워크"))
        assertEquals(true, text(AppError.Server(503)).contains("잠시 뒤"))
        assertEquals(true, text(AppError.Rejected("NOT_FOUND")).contains("찾을 수 없"))
        assertEquals(true, text(AppError.Rejected("BAD_REQUEST")).contains("요청 내용"))
        assertEquals("만 14세 이상만 가입할 수 있음", text(AppError.Rejected("UNDER_14")))
    }

    @Test
    fun `목표·계획 범위 오류와 기록 공개 범위 거부는 사유 문구`() {
        assertEquals("주간 목표는 1~7회와 0km보다 긴 거리로 정해 주세요", text(AppError.Rejected("GOAL_INVALID")))
        assertEquals("계획 거리는 0km보다 길게, 목표 페이스는 0초보다 크게 넣어 주세요", text(AppError.Rejected("PLAN_INVALID")))
        assertEquals("지난 날짜에는 계획을 넣을 수 없어요", text(AppError.Rejected("PAST_DATE")))
        assertEquals("비공개 기록이라 볼 수 없어요", text(AppError.Rejected("RECORD_PRIVATE")))
        assertEquals("같은 크루원만 볼 수 있는 기록이에요", text(AppError.Rejected("RECORD_CREW_ONLY")))
    }

    @Test
    fun `네트워크·인증·저장 오류는 각자 문구`() {
        assertEquals(true, text(AppError.Network).startsWith("네트워크"))
        assertEquals(true, text(AppError.Unauthorized).contains("로그인"))
        assertEquals(true, text(AppError.Storage).contains("저장"))
    }

    @Test
    fun `서버 빈 곳 메우기에서 생긴 사유 코드 7개는 모두 일반 문구가 아닌 사유 문구를 갖는다`() {
        val generic = rejectedText("SOMETHING_NEW")
        listOf(
            "NICKNAME_TAKEN",
            "CREW_NAME_TAKEN",
            "BATTLE_IN_PROGRESS",
            "MATCH_FAILED",
            "FACE_IMAGE_NOT_FOUND",
            "SELFIE_RETRY_LIMIT",
            "TOO_MANY_ATTEMPTS"
        ).forEach { code ->
            assertEquals(false, rejectedText(code) == generic, "$code 문구가 없다")
        }
        assertEquals(true, rejectedText("NICKNAME_TAKEN").contains("닉네임"))
        assertEquals(true, rejectedText("TOO_MANY_ATTEMPTS").contains("잠시 뒤"))
    }
}
