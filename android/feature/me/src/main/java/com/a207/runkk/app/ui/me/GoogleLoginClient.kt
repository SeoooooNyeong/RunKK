package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import javax.inject.Inject

/**
 * 구글 로그인(C-007): Credential Manager의 "Google로 로그인" 흐름으로 ID 토큰을 받아 서버 POST /auth/login에 넘긴다.
 * 웹 클라이언트 ID가 없으면 SDK를 부르지 않고 "준비 중"을 돌려준다(키 없이도 빌드·시험이 된다).
 */
class GoogleLoginClient @Inject constructor(
    private val keys: SocialLoginKeys,
    private val source: GoogleIdTokenSource
) : SocialLoginClient {
    override val provider: ProviderType = ProviderType.GOOGLE

    override suspend fun signIn(context: Context): Outcome<ProviderToken> {
        val clientId = keys.googleWebClientId.trim()
        if (clientId.isEmpty()) return Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
        return when (val token = source.idToken(context, clientId)) {
            is Outcome.Success -> Outcome.Success(ProviderToken(ProviderType.GOOGLE, token.value))
            is Outcome.Failure -> token
        }
    }
}
