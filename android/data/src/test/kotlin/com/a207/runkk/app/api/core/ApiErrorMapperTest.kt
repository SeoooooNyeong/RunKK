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
    fun `5xx와 408은 Network가 아니라 다시 시도할 수 있는 Server`() {
        listOf(500, 503, 408).forEach { status ->
            assertEquals(AppError.Server(status), ApiErrorMapper.fromHttp(status, """{"code":"X"}"""), "HTTP $status")
        }
    }

    @Test
    fun `429는 본문 사유 코드가 있으면 거부로, 없으면 다시 시도할 수 있는 Server로 본다`() {
        assertEquals(
            AppError.Rejected("TOO_MANY_ATTEMPTS"),
            ApiErrorMapper.fromHttp(429, """{"code":"TOO_MANY_ATTEMPTS","message":"잠시 뒤 다시 시도해 주세요"}""")
        )
        assertEquals(AppError.Server(429), ApiErrorMapper.fromHttp(429, null))
        assertEquals(AppError.Server(429), ApiErrorMapper.fromHttp(429, "<html>"))
    }

    private fun http(status: Int, body: String) = retrofit2.HttpException(
        retrofit2.Response.error<Any>(status, body.toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `로그인 실패 401·503은 본문 code를 사유 코드로 읽고 Unauthorized로 바꾸지 않는다`() {
        assertEquals(
            AppError.Rejected("AUTH_FAILED"),
            ApiErrorMapper.toLoginError(http(401, """{"code":"AUTH_FAILED","message":"인증 실패"}"""))
        )
        assertEquals(
            AppError.Rejected("PROVIDER_UNAVAILABLE"),
            ApiErrorMapper.toLoginError(http(503, """{"code":"PROVIDER_UNAVAILABLE","message":"제공자 무응답"}"""))
        )
        assertEquals(AppError.Server(503), ApiErrorMapper.toLoginError(http(503, "")))
    }

    @Test
    fun `탈퇴 거부 409는 본문 code를 그대로 쓰고 code가 없으면 WITHDRAWAL_REJECTED`() {
        assertEquals(
            AppError.Rejected("LEADER_MUST_TRANSFER"),
            ApiErrorMapper.toWithdrawalError(http(409, """{"code":"LEADER_MUST_TRANSFER","message":"크루장 양도 필요"}"""))
        )
        assertEquals(
            AppError.Rejected("BATTLE_IN_PROGRESS"),
            ApiErrorMapper.toWithdrawalError(http(409, """{"code":"BATTLE_IN_PROGRESS","message":"진행 중인 대결"}"""))
        )
        assertEquals(AppError.Rejected("WITHDRAWAL_REJECTED"), ApiErrorMapper.toWithdrawalError(http(409, "")))
        assertEquals(AppError.Server(500), ApiErrorMapper.toWithdrawalError(http(500, "")))
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
        assertEquals(AppError.Rejected("NOT_FOUND"), ApiErrorMapper.toAppError(http(404, "")))
        assertEquals(AppError.Rejected("BAD_REQUEST"), ApiErrorMapper.toAppError(http(400, "")))
        assertEquals(AppError.Server(500), ApiErrorMapper.toAppError(http(500, "")))
    }

    @Test
    fun `해독할 수 없는 응답은 Server(0)`() {
        assertEquals(AppError.Server(0), ApiErrorMapper.toAppError(kotlinx.serialization.SerializationException("x")))
    }
}
