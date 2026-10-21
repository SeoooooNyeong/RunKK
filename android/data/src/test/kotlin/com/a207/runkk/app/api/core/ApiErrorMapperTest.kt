package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

/** ApiErrorMapper(6.1.14): HTTP 상태·본문 → AppError. 일시 오류는 다시 시도할 수 있는 Network다. */
class ApiErrorMapperTest {
    @Test
    fun `409는 본문 사유 코드로 거부된다`() {
        assertEquals(
            AppError.Rejected("NICKNAME_INVALID"),
            ApiErrorMapper.fromHttp(409, """{"code":"NICKNAME_INVALID"}""")
        )
    }

    @Test
    fun `401은 Unauthorized`() {
        assertEquals(AppError.Unauthorized, ApiErrorMapper.fromHttp(401, null))
    }

    @Test
    fun `5xx와 408·429는 다시 시도할 수 있는 Network`() {
        listOf(500, 503, 408, 429).forEach { status ->
            assertEquals(AppError.Network, ApiErrorMapper.fromHttp(status, """{"code":"X"}"""), "HTTP $status")
        }
    }

    @Test
    fun `본문 없는 4xx는 HTTP 상태 코드로 거부된다`() {
        assertEquals(AppError.Rejected("HTTP_404"), ApiErrorMapper.fromHttp(404, ""))
    }
}
