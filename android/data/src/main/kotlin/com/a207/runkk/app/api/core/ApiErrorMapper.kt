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
 * - 연결 실패·시간 초과(IOException) → Network. 서버가 응답한 경우는 Network로 보지 않는다.
 * - 401 → Unauthorized(TokenAuthenticator가 갱신까지 실패한 경우)
 * - 5xx·408·429 → Server(status). 잠시 뒤 다시 시도할 수 있는 실패. 해독할 수 없는 응답·예상 밖 예외는 Server(0)
 * - 그 밖의 4xx(409 포함)는 본문 code를 사유 코드로 하는 Rejected(code). 서버 web.error.ErrorResponse(code, message)의 code를
 *   그대로 쓰고, 본문에 없으면 404는 NOT_FOUND, 400은 BAD_REQUEST, 나머지는 HTTP_상태.
 */
object ApiErrorMapper {
    private const val UNAUTHORIZED = 401
    private const val SERVER_ERROR = 500
    private const val BAD_REQUEST = 400
    private const val NOT_FOUND = 404
    private const val CONFLICT = 409

    /** 해독할 수 없는 응답·예상 밖 예외를 나타내는 Server의 status. */
    private const val UNREADABLE = 0

    /** 잠시 뒤 다시 하면 될 4xx(요청 시간 초과, 요청 과다). 재전송(UploadWorker)이 멈추지 않게 Network로 본다. */
    private val TRANSIENT = setOf(408, 429)
    private val errorJson = Json { ignoreUnknownKeys = true }

    fun toAppError(error: Throwable): AppError = when (error) {
        is HttpException -> fromHttp(error.code(), error.response()?.errorBody()?.string())
        is IOException -> AppError.Network
        is SerializationException -> AppError.Server(UNREADABLE)
        else -> AppError.Server(UNREADABLE)
    }

    /**
     * 로그인 실패. 서버는 인증 실패(401)·제공자 무응답(503)도 사유를 본문 reason에 담아 돌려준다(web.auth.AuthController
     * LoginResponse.failed). 그 사유가 있으면 Rejected(reason)로 보고, 없으면 일반 규칙을 따른다.
     */
    fun toLoginError(error: Throwable): AppError {
        if (error is HttpException) {
            val reason = decodeBody(error.response()?.errorBody()?.string())?.reason
            if (!reason.isNullOrBlank()) return AppError.Rejected(reason)
        }
        return toAppError(error)
    }

    /**
     * 탈퇴 거부. 서버는 가드가 막으면 409와 함께 ErrorResponse 대신 WithdrawalResult(withdrawn=false, rejectionReason: 가드 문구)를
     * 돌려준다(web.me.MeController.withdraw). 문구를 앱 사유 코드로 바꾼다: 크루장 양도 필요 → LEADER_MUST_TRANSFER,
     * 진행 중인 대결 → BATTLE_IN_PROGRESS, 그 밖 → WITHDRAWAL_REJECTED.
     */
    fun toWithdrawalError(error: Throwable): AppError {
        if (error is HttpException && error.code() == CONFLICT) {
            val reason = decodeBody(error.response()?.errorBody()?.string())?.rejectionReason
            if (reason != null) {
                return AppError.Rejected(
                    when {
                        "크루장" in reason -> "LEADER_MUST_TRANSFER"
                        "대결" in reason -> "BATTLE_IN_PROGRESS"
                        else -> "WITHDRAWAL_REJECTED"
                    }
                )
            }
        }
        return toAppError(error)
    }

    fun fromHttp(status: Int, body: String?): AppError {
        if (status == UNAUTHORIZED) return AppError.Unauthorized
        if (status >= SERVER_ERROR || status in TRANSIENT) return AppError.Server(status)
        return AppError.Rejected(reasonCode(body) ?: defaultCode(status))
    }

    /** REST 호출 하나를 Outcome으로 감싼다. 코루틴 취소는 그대로 전파한다. */
    suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(toAppError(e))
    }

    private fun defaultCode(status: Int): String = when (status) {
        NOT_FOUND -> "NOT_FOUND"
        BAD_REQUEST -> "BAD_REQUEST"
        else -> "HTTP_$status"
    }

    private fun reasonCode(body: String?): String? = decodeBody(body)?.code

    private fun decodeBody(body: String?): ErrorBody? {
        if (body.isNullOrBlank()) return null
        return try {
            errorJson.decodeFromString(ErrorBody.serializer(), body)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @Serializable
    private data class ErrorBody(
        val code: String? = null,
        val reason: String? = null,
        val rejectionReason: String? = null,
        val message: String? = null
    )
}
