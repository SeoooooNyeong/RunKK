package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * 서버·네트워크 오류를 앱 오류로 바꾼다(6.1.14, 13.2). 예외는 여기서 멈추고 도메인·화면에는 Outcome으로만 올라간다.
 * - 연결 실패·응답 해독 실패 → Network
 * - 401 → Unauthorized(TokenAuthenticator가 갱신까지 실패한 경우)
 * - 409 + 사유 코드 → Rejected(code). 서버 web.error.ErrorResponse(code, message)의 code를 그대로 쓴다.
 * - 그 밖의 4xx는 본문 code(없으면 HTTP_상태)로 Rejected, 5xx는 Network(잠시 뒤 다시 시도할 수 있는 실패)
 */
object ApiErrorMapper {
    private const val UNAUTHORIZED = 401
    private const val SERVER_ERROR = 500
    private val errorJson = Json { ignoreUnknownKeys = true }

    fun toAppError(error: Throwable): AppError = when (error) {
        is HttpException -> fromHttp(error.code(), error.response()?.errorBody()?.string())
        is IOException -> AppError.Network
        is SerializationException -> AppError.Network
        else -> AppError.Network
    }

    fun fromHttp(status: Int, body: String?): AppError {
        if (status == UNAUTHORIZED) return AppError.Unauthorized
        if (status >= SERVER_ERROR) return AppError.Network
        return AppError.Rejected(reasonCode(body) ?: "HTTP_$status")
    }

    /** REST 호출 하나를 Outcome으로 감싼다. 코루틴 취소는 그대로 전파한다. */
    suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(toAppError(e))
    }

    private fun reasonCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            errorJson.decodeFromString(ErrorBody.serializer(), body).code
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @Serializable
    private data class ErrorBody(val code: String? = null, val message: String? = null)
}
