package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import javax.inject.Inject

/**
 * 카카오 로그인(C-007): Kakao SDK로 접근 토큰을 받아 서버 POST /auth/login에 넘긴다. 네이티브 앱 키가 없으면 SDK를 부르지 않고
 * "준비 중"을 돌려준다(키 없이도 빌드·시험이 된다).
 */
class KakaoLoginClient @Inject constructor(
    private val keys: SocialLoginKeys,
    private val source: KakaoAccessTokenSource
) : SocialLoginClient {
    override val provider: ProviderType = ProviderType.KAKAO

    override suspend fun signIn(context: Context): Outcome<ProviderToken> {
        val appKey = keys.kakaoNativeAppKey.trim()
        if (appKey.isEmpty() && source.isNativeAppKeyRequired) return Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
        return when (val token = source.accessToken(context, appKey)) {
            is Outcome.Success -> Outcome.Success(ProviderToken(ProviderType.KAKAO, token.value))
            is Outcome.Failure -> token
        }
    }
}
