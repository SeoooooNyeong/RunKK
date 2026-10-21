package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

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
    fun `5xx와 408·429는 Network가 아니라 다시 시도할 수 있는 Server`() {
        listOf(500, 503, 408, 429).forEach { status ->
            assertEquals(AppError.Server(status), ApiErrorMapper.fromHttp(status, """{"code":"X"}"""), "HTTP $status")
        }
    }

    @Test
    fun `404와 400은 본문 사유 코드로, 본문이 없으면 NOT_FOUND·BAD_REQUEST로 거부된다`() {
        assertEquals(AppError.Rejected("NOT_FOUND"), ApiErrorMapper.fromHttp(404, ""))
        assertEquals(AppError.Rejected("RUN_NOT_FOUND"), ApiErrorMapper.fromHttp(404, """{"code":"RUN_NOT_FOUND"}"""))
        assertEquals(AppError.Rejected("BAD_REQUEST"), ApiErrorMapper.fromHttp(400, null))
        assertEquals(
            AppError.Rejected("SETTING_INVALID"),
            ApiErrorMapper.fromHttp(400, """{"code":"SETTING_INVALID"}""")
        )
        assertEquals(AppError.Rejected("HTTP_403"), ApiErrorMapper.fromHttp(403, "<html>"))
    }

    @Test
    fun `연결 실패와 시간 초과만 Network`() {
        assertEquals(AppError.Network, ApiErrorMapper.toAppError(java.net.ConnectException()))
        assertEquals(AppError.Network, ApiErrorMapper.toAppError(java.net.SocketTimeoutException()))
        assertEquals(AppError.Network, ApiErrorMapper.toAppError(java.io.IOException()))
    }

    @Test
    fun `HttpException은 상태 코드로 구분한다`() {
        fun http(status: Int, body: String) = retrofit2.HttpException(
            retrofit2.Response.error<Any>(
                status,
                body.toResponseBody("application/json".toMediaType())
            )
        )
        assertEquals(AppError.Rejected("NOT_FOUND"), ApiErrorMapper.toAppError(http(404, "")))
        assertEquals(AppError.Rejected("BAD_REQUEST"), ApiErrorMapper.toAppError(http(400, "")))
        assertEquals(AppError.Server(500), ApiErrorMapper.toAppError(http(500, "")))
    }

    @Test
    fun `해독할 수 없는 응답은 Server(0)`() {
        assertEquals(AppError.Server(0), ApiErrorMapper.toAppError(kotlinx.serialization.SerializationException("x")))
    }
}
