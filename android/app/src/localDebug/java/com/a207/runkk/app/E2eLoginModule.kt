package com.a207.runkk.app

import com.a207.runkk.app.ui.me.KakaoAccessTokenSource
import com.a207.runkk.app.ui.me.KakaoTokenOverride
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Named

/** 토큰 값이 있을 때만 대체 창구 자리(KakaoTokenOverride)에 시험용 창구를 넣는다. 비면 Kakao SDK 그대로다. */
@Module
@InstallIn(SingletonComponent::class)
internal object E2eLoginModule {
    @Provides
    @ElementsIntoSet
    @KakaoTokenOverride
    fun e2eKakaoTokenSource(
        @Named(E2eBuildConfigModule.E2E_KAKAO_TOKEN_NAME) token: String
    ): Set<KakaoAccessTokenSource> = e2eKakaoTokenSources(token)
}
