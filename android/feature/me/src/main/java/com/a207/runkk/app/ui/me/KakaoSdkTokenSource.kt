package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Kakao SDK 구현. 처음 부를 때 KakaoSdk.init을 하고, 카카오톡이 있으면 카카오톡으로, 없거나 카카오톡 로그인이 안 되면(취소 제외)
 * 카카오계정(웹)으로 로그인한다. 취소는 LOGIN_CANCELLED, 그 밖의 실패는 AUTH_FAILED.
 */
class KakaoSdkTokenSource @Inject constructor() : KakaoAccessTokenSource {
    override suspend fun accessToken(context: Context, nativeAppKey: String): Outcome<String> {
        initOnce(context, nativeAppKey)
        val users = UserApiClient.instance
        if (users.isKakaoTalkLoginAvailable(context)) {
            val talk = login { callback -> users.loginWithKakaoTalk(context, callback = callback) }
            // 카카오톡에서 사용자가 취소했으면 계정 로그인으로 넘기지 않는다.
            if (talk is Outcome.Success || talk == Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED))) return talk
        }
        return login { callback -> users.loginWithKakaoAccount(context, callback = callback) }
    }

    private suspend fun login(start: ((OAuthToken?, Throwable?) -> Unit) -> Unit): Outcome<String> =
        suspendCancellableCoroutine { continuation ->
            start { token, error ->
                val result = when {
                    token != null -> Outcome.Success(token.accessToken)

                    error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                        Outcome.Failure(AppError.Rejected(LOGIN_CANCELLED))

                    else -> Outcome.Failure(AppError.Rejected(KAKAO_AUTH_FAILED))
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun initOnce(context: Context, nativeAppKey: String) {
        synchronized(KakaoSdkTokenSource::class) {
            if (initializedKey != nativeAppKey) {
                KakaoSdk.init(context.applicationContext, nativeAppKey)
                initializedKey = nativeAppKey
            }
        }
    }

    private companion object {
        const val KAKAO_AUTH_FAILED = "AUTH_FAILED"

        @Volatile
        var initializedKey: String? = null
    }
}
