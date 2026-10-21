package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.Outcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Named

/**
 * E2E 시험 전용 카카오 토큰 창구(local 플레이버 + debug 빌드의 src/localDebug에만 있다, e2e/README.md).
 * Kakao SDK 대신 빌드 속성 RUNKK_E2E_KAKAO_TOKEN 값을 그대로 돌려준다. 서버는 이 토큰을 KAKAO_API_BASE_URL(시험 때는 카카오
 * 스텁 e2e/kakao_stub.py)에 물어 실제 검증 절차(회원 ID·앱 ID 확인)를 그대로 거친다. 서버 쪽 인증 우회는 없다.
 */
internal class E2eKakaoTokenSource(private val token: String) : KakaoAccessTokenSource {
    override val requiresNativeAppKey: Boolean = false

    override suspend fun accessToken(context: Context, nativeAppKey: String): Outcome<String> = Outcome.Success(token)
}

/** 토큰 값이 있을 때만 대체 창구 자리(KakaoTokenOverride)에 시험용 창구를 넣는다. 비면 Kakao SDK 그대로다. */
@Module
@InstallIn(SingletonComponent::class)
internal object E2eLoginModule {
    @Provides
    @ElementsIntoSet
    @KakaoTokenOverride
    fun e2eKakaoTokenSource(@Named(E2E_KAKAO_TOKEN_NAME) token: String): Set<KakaoAccessTokenSource> =
        e2eKakaoTokenSources(token)

    private const val E2E_KAKAO_TOKEN_NAME = "runkk.e2e.kakaoToken"
}

internal fun e2eKakaoTokenSources(token: String): Set<KakaoAccessTokenSource> =
    token.trim().let { if (it.isEmpty()) emptySet() else setOf(E2eKakaoTokenSource(it)) }
