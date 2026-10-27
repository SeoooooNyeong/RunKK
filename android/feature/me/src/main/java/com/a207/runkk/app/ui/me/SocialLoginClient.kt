package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType

/**
 * 제공자 SDK로 로그인 토큰을 받는다(6.1.14). Activity가 필요한 화면 흐름이라 ui.me에 둔다.
 * 실패(취소·SDK 오류)는 던지지 않고 Outcome.Failure(Rejected(사유))로 돌려준다(13.2 오류 규칙).
 */
interface SocialLoginClient {
    val provider: ProviderType

    /** [context]는 SDK 로그인 화면을 띄울 Activity다. */
    suspend fun signIn(context: Context): Outcome<ProviderToken>
}

/** 대체 창구가 있으면 그것을, 없으면 Kakao SDK 창구를 쓴다. 대체 창구가 둘 이상이면 설정 오류다. */
internal fun chooseKakaoTokenSource(
    overrides: Set<KakaoAccessTokenSource>,
    sdk: KakaoAccessTokenSource
): KakaoAccessTokenSource {
    check(overrides.size <= 1) { "카카오 토큰 대체 창구는 하나만 둘 수 있다: ${overrides.size}개" }
    return overrides.firstOrNull() ?: sdk
}

/** SDK 연동 전 사유 코드(RunkkErrorText 문구 "이 로그인 방법은 아직 준비 중이에요"). */
internal const val SDK_NOT_READY = "PROVIDER_SDK_NOT_READY"

/** 사용자가 제공자 로그인 화면을 닫았다(RunkkErrorText "로그인을 취소했어요"). */
internal const val LOGIN_CANCELLED = "LOGIN_CANCELLED"
