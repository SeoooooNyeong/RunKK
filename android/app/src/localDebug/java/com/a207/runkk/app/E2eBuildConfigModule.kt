package com.a207.runkk.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * E2E 시험 전용(local 플레이버 + debug 빌드의 src/localDebug에만 있다). 빌드 속성 RUNKK_E2E_KAKAO_TOKEN을 그래프에 내준다.
 * 비어 있으면 시험용 카카오 창구가 꺼진다(ui.me.E2eLoginModule).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object E2eBuildConfigModule {
    @Provides
    @Named(E2E_KAKAO_TOKEN_NAME)
    fun e2eKakaoToken(): String = BuildConfig.E2E_KAKAO_TOKEN

    /** ui.me.E2eLoginModule이 같은 이름으로 받는다(app → ui.me import는 5.1.2 표 밖이라 문자열로 맞춘다). */
    const val E2E_KAKAO_TOKEN_NAME = "runkk.e2e.kakaoToken"
}
