package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import javax.inject.Inject

/**
 * 제공자 SDK로 로그인 토큰을 받는다(6.1.14). Activity가 필요한 화면 흐름이라 ui.me에 둔다.
 * 실패(취소·SDK 오류)는 던지지 않고 Outcome.Failure(Rejected(사유))로 돌려준다(13.2 오류 규칙).
 */
interface SocialLoginClient {
    val provider: ProviderType

    /** [context]는 SDK 로그인 화면을 띄울 Activity다. */
    suspend fun signIn(context: Context): Outcome<ProviderToken>
}

/**
 * 카카오 로그인(C-007). Kakao SDK·앱 키 연동은 통합 단계에서 붙인다. 그 전에는 "준비 중"을 돌려준다.
 */
class KakaoLoginClient @Inject constructor() : SocialLoginClient {
    override val provider: ProviderType = ProviderType.KAKAO

    override suspend fun signIn(context: Context): Outcome<ProviderToken> =
        Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
}

/**
 * 구글 로그인(C-007). Credential Manager·웹 클라이언트 ID 연동은 통합 단계에서 붙인다. 그 전에는 "준비 중"을 돌려준다.
 */
class GoogleLoginClient @Inject constructor() : SocialLoginClient {
    override val provider: ProviderType = ProviderType.GOOGLE

    override suspend fun signIn(context: Context): Outcome<ProviderToken> =
        Outcome.Failure(AppError.Rejected(SDK_NOT_READY))
}

/** SDK 연동 전 사유 코드(RunkkErrorText 문구 "이 로그인 방법은 아직 준비 중이에요"). */
internal const val SDK_NOT_READY = "PROVIDER_SDK_NOT_READY"
